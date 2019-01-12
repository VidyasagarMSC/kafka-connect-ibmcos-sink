package com.ibm.eventstreams.connect.cossink.completion;

import static org.junit.Assert.assertEquals;

import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RecordCountCriteriaTest {

    @Mock
    SinkRecord mockSinkRecord;

    @Mock
    AsyncCompleter mockAsyncCompleter;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    // The first() method should return FirstResult.COMPLETE if the record count
    // is set to one.
    @Test
    public void firstResturnsCompleteIfCountEqualsOne() {
        RecordCountCriteria rcc = new RecordCountCriteria(1);
        FirstResult result = rcc.first(mockSinkRecord, mockAsyncCompleter);
        assertEquals(FirstResult.COMPLETE, result);
    }

    // The first() methods should return FirstResult.INCOMPLETE if the record count
    // is set to a value greater than one.
    @Test
    public void firstReturnsIncompleteIfCountGreatherThanOne() {
        for (int i = 2; i < 1000; i++) {
            RecordCountCriteria rcc = new RecordCountCriteria(i);
            FirstResult result = rcc.first(mockSinkRecord, mockAsyncCompleter);
            assertEquals(FirstResult.INCOMPLETE, result);
        }
    }

    // The next() method should return NextResult.INCOMPLETE until the record count
    // records have been seen by the criteria, at which point it should return
    // COMPLETE_INCLUSIVE
    @Test
    public void nextReturnsIncompleteUntilEnoughRecordsSeen() {
        for (int i = 2; i < 1000; i++) {
            RecordCountCriteria rcc = new RecordCountCriteria(i);
            rcc.first(mockSinkRecord, mockAsyncCompleter);
            for (int j = 1; j < i; j++) {
                NextResult result = rcc.next(mockSinkRecord);
                if (j == i-1) {
                    assertEquals(NextResult.COMPLETE_INCLUSIVE, result);
                } else {
                    assertEquals(NextResult.INCOMPLETE, result);
                }
            }
        }
    }
}
