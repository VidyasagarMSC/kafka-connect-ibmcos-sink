package com.ibm.eventstreams.connect.cossink.partitionwriter;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.connect.sink.SinkRecord;

import com.ibm.cos.Bucket;
import com.ibm.eventstreams.connect.cossink.completion.AsyncCompleter;
import com.ibm.eventstreams.connect.cossink.completion.CompletionCriteriaSet;
import com.ibm.eventstreams.connect.cossink.completion.FirstResult;

class OSPartitionWriter extends RequestProcessor<RequestType> implements PartitionWriter {

    private final Bucket bucket;
    private final CompletionCriteriaSet completionCriteria;

    private OSObject osObject;
    private Long objectCount = 0L;

    private AtomicReference<Long> lastOffset = new AtomicReference<>();

    OSPartitionWriter(final Bucket bucket, final CompletionCriteriaSet completionCriteria) {
        super(RequestType.CLOSE);
        this.bucket = bucket;
        this.completionCriteria = completionCriteria;
    }

    @Override
    public Long preCommit() {
        Long offset = lastOffset.getAndSet(null);
        if (offset == null) {
            return null;
        }
        // Commit the offset one beyond the last record processed. This is
        // where processing should resume from if the task fails.
        return offset + 1;
    }

    @Override
    public void put(final SinkRecord record) {
        queue(RequestType.PUT, record);
    }

    @Override
    public void close() {
        super.close();
    }

    private void writeObject() {
        objectCount++;
        osObject.write(bucket);
        lastOffset.set(osObject.lastOffset());
        osObject = null;
        completionCriteria.complete();
    }

    private void startObject(SinkRecord record) {
        osObject = new OSObject();
        osObject.put(record);
        FirstResult result = completionCriteria.first(record, new AsyncCompleterImpl(this, objectCount));
        if (result == FirstResult.COMPLETE) {
            writeObject();
        }
    }

    private boolean haveStartedObject() {
        return osObject != null;
    }

    private void addToExistingObject(SinkRecord record) {
        osObject.put(record);
    }

    @Override
    void process(RequestType type, Object context) {
        switch (type) {
        case CLOSE:
            break;

        case PUT:
            final SinkRecord record = (SinkRecord)context;
            if (haveStartedObject()) {
                switch(completionCriteria.next(record)) {
                case COMPLETE_INCLUSIVE:
                    addToExistingObject(record);
                    writeObject();
                    break;
                case COMPLETE_NON_INCLUSIVE:
                    writeObject();
                    startObject(record);
                    break;
                case INCOMPLETE:
                    addToExistingObject(record);
                    break;
                }
            } else {
                startObject(record);
            }
            break;

        case DEADLINE:
            final long deadlineObjectCount = (long)context;
            if (deadlineObjectCount == objectCount) {
                writeObject();
            }
            break;
        }
    }

    static class AsyncCompleterImpl implements AsyncCompleter {

        private final OSPartitionWriter writer;
        private final long objectCount;

        AsyncCompleterImpl(OSPartitionWriter writer, long objectCount) {
            this.writer = writer;
            this.objectCount = objectCount;
        }

        @Override
        public void asyncComplete() {
            writer.queue(RequestType.DEADLINE, this.objectCount);
        }

    }

}
