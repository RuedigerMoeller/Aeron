/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.util.concurrent.logbuffer;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import uk.co.real_logic.aeron.util.concurrent.AtomicBuffer;

import static java.lang.Integer.valueOf;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.FrameDescriptor.FRAME_ALIGNMENT;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.FrameDescriptor.lengthOffset;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.FrameDescriptor.typeOffset;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.LogBufferDescriptor.TAIL_COUNTER_OFFSET;

public class MtuScannerTest
{
    private static final int LOG_BUFFER_SIZE = 1024 * 16;
    private static final int STATE_BUFFER_SIZE = 1024;
    private static final int MTU_LENGTH = 1024;
    private static final int HEADER_LENGTH = 32;
    private static final short MSG_TYPE = 7;

    private final AtomicBuffer logBuffer = mock(AtomicBuffer.class);
    private final AtomicBuffer stateBuffer = mock(AtomicBuffer.class);

    private MtuScanner scanner;

    @Before
    public void setUp()
    {
        when(valueOf(logBuffer.capacity())).thenReturn(valueOf(LOG_BUFFER_SIZE));
        when(valueOf(stateBuffer.capacity())).thenReturn(valueOf(STATE_BUFFER_SIZE));

        scanner = new MtuScanner(logBuffer, stateBuffer, MTU_LENGTH, HEADER_LENGTH);
    }

    @Test
    public void shouldReportUnderlyingCapacity()
    {
        assertThat(valueOf(scanner.capacity()), is(valueOf(LOG_BUFFER_SIZE)));
    }

    @Test
    public void shouldReportMtu()
    {
        assertThat(valueOf(scanner.mtuLength()), is(valueOf(MTU_LENGTH)));
    }

    @Test
    public void shouldReturnFalseOnEmptyLog()
    {
        assertFalse(scanner.scan());
    }

    @Test
    public void shouldScanSingleMessage()
    {
        final int frameOffset = 0;
        when(valueOf(stateBuffer.getIntVolatile(TAIL_COUNTER_OFFSET)))
            .thenReturn(valueOf(FRAME_ALIGNMENT));
        when(valueOf(logBuffer.getIntVolatile(lengthOffset(frameOffset))))
            .thenReturn(valueOf(FRAME_ALIGNMENT));
        when(Short.valueOf(logBuffer.getShort(typeOffset(frameOffset), LITTLE_ENDIAN)))
            .thenReturn(Short.valueOf(MSG_TYPE));

        assertTrue(scanner.scan());
        assertThat(valueOf(scanner.offset()), is(valueOf(0)));
        assertThat(valueOf(scanner.length()), is(valueOf(FRAME_ALIGNMENT)));
        assertFalse(scanner.isComplete());

        final InOrder inOrder = inOrder(stateBuffer, logBuffer);
        inOrder.verify(stateBuffer).getIntVolatile(TAIL_COUNTER_OFFSET);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(frameOffset));
        inOrder.verify(logBuffer).getShort(typeOffset(frameOffset), LITTLE_ENDIAN);
    }
}