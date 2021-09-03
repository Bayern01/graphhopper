package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfReader;
import com.graphhopper.reader.osm.pbf.Sink;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLStreamException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class HdfsInputFile implements Sink, OSMInput{
    private static final int MAX_BATCH_SIZE = 1_000;
    private final InputStream bis;
    private final BlockingQueue<ReaderElement> itemQueue;
    private final Queue<ReaderElement> itemBatch;
    private boolean eof;
    private URI uri = null;

    private boolean binary = false;
    private PbfReader pbfReader;
    private Thread pbfReaderThread;
    private boolean hasIncomingData;
    private int workerThreads = -1;

    public HdfsInputFile(String uri) throws IOException {
        this.uri = URI.create(uri);
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(this.uri, conf);

        InputStream in = null;

        try {
            in = fs.open(new Path(uri));
        } catch (IOException e) {
            System.out.println("Your specified HdfsOSM file does not exist");
            throw e;
        }

        DataInputStream reader = new DataInputStream(in);
        binary = true;
        bis = reader;
        itemQueue = new LinkedBlockingQueue<>(50_000);
        itemBatch = new ArrayDeque<>(MAX_BATCH_SIZE);
    }

    public HdfsInputFile open() throws XMLStreamException {
        if (binary) {
            openPBFReader(bis);
        }
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public HdfsInputFile setWorkerThreads(int num) {
        workerThreads = num;
        return this;
    }

    @Override
    public ReaderElement getNext() throws XMLStreamException {
        if (eof)
            throw new IllegalStateException("EOF reached");

        ReaderElement item = null;
        if (binary)
            item = getNextPBF();

        if (item != null)
            return item;

        eof = true;
        return null;
    }

    public boolean isEOF() {
        return eof;
    }

    @Override
    public void close() throws IOException {
        try {
            if (binary)
                pbfReader.close();
        } finally {
            eof = true;
            bis.close();
            // if exception happened on OSMInputFile-thread we need to shutdown the pbf handling
            if (pbfReaderThread != null && pbfReaderThread.isAlive())
                pbfReaderThread.interrupt();
        }
    }

    private void openPBFReader(InputStream stream) {
        hasIncomingData = true;
        if (workerThreads <= 0)
            workerThreads = 1;

        pbfReader = new PbfReader(stream, this, workerThreads);
        pbfReaderThread = new Thread(pbfReader, "PBF Reader");
        pbfReaderThread.start();
    }

    @Override
    public void process(ReaderElement item) {
        try {
            // blocks if full
            itemQueue.put(item);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public int getUnprocessedElements() {
        return itemQueue.size() + itemBatch.size();
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }

    private ReaderElement getNextPBF() {
        while (itemBatch.isEmpty()) {
            if (!hasIncomingData && itemQueue.isEmpty()) {
                return null; // signal EOF
            }

            if (itemQueue.drainTo(itemBatch, MAX_BATCH_SIZE) == 0) {
                try {
                    ReaderElement element = itemQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (element != null) {
                        return element; // short circuit
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null; // signal EOF
                }
            }
        }

        return itemBatch.poll();
    }
}
