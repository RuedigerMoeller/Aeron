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

package uk.co.real_logic.aeron.common.concurrent.logbuffer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.agrona.BitUtil.align;
import static uk.co.real_logic.aeron.common.concurrent.logbuffer.FrameDescriptor.*;
import static uk.co.real_logic.aeron.common.concurrent.logbuffer.LogBufferDescriptor.*;

import static uk.co.real_logic.aeron.common.protocol.HeaderFlyweight.HDR_TYPE_DATA;

public class LogReaderTest
{
    private static final int LOG_BUFFER_CAPACITY = LogBufferDescriptor.MIN_LOG_SIZE;
    private static final int STATE_BUFFER_CAPACITY = STATE_BUFFER_LENGTH;
    private static final int HEADER_LENGTH = Header.LENGTH;

    private final UnsafeBuffer logBuffer = mock(UnsafeBuffer.class);
    private final UnsafeBuffer stateBuffer = spy(new UnsafeBuffer(new byte[STATE_BUFFER_CAPACITY]));
    private final DataHandler handler = Mockito.mock(DataHandler.class);

    private LogReader logReader;

    @Before
    public void setUp()
    {
        when(logBuffer.capacity()).thenReturn(LOG_BUFFER_CAPACITY);

        logReader = new LogReader(logBuffer, stateBuffer);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenCapacityNotMultipleOfAlignment()
    {
        final int logBufferCapacity = LogBufferDescriptor.MIN_LOG_SIZE + FRAME_ALIGNMENT + 1;
        when(logBuffer.capacity()).thenReturn(logBufferCapacity);

        logReader = new LogReader(logBuffer, stateBuffer);
    }

    @Test
    public void shouldReadFirstMessage()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;

        when(logBuffer.getIntVolatile(lengthOffset(0))).thenReturn(frameLength);
        when(logBuffer.getShort(typeOffset(0))).thenReturn((short)HDR_TYPE_DATA);

        assertThat(logReader.read(handler, Integer.MAX_VALUE), is(1));

        final InOrder inOrder = inOrder(logBuffer, stateBuffer);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(0));
        verify(handler).onData(eq(logBuffer), eq(HEADER_LENGTH), eq(msgLength), any(Header.class));
    }

    @Test
    public void shouldNotReadWhenLimitIsZero()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;

        when(logBuffer.getIntVolatile(lengthOffset(0))).thenReturn(frameLength);

        assertThat(logReader.read(handler, 0), is(0));

        verifyZeroInteractions(handler);
    }

    @Test
    public void shouldNotReadPastTail()
    {
        assertThat(logReader.read(handler, Integer.MAX_VALUE), is(0));

        verify(logBuffer).getIntVolatile(lengthOffset(0));
        verify(handler, never()).onData(any(), anyInt(), anyInt(), any());
    }

    @Test
    public void shouldReadOneLimitedMessage()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;
        final int alignedFrameLength = align(frameLength, FRAME_ALIGNMENT);

        when(logBuffer.getIntVolatile(anyInt())).thenReturn(frameLength);
        when(stateBuffer.getIntVolatile(TAIL_COUNTER_OFFSET)).thenReturn(alignedFrameLength * 2);
        when(logBuffer.getShort(anyInt())).thenReturn((short)HDR_TYPE_DATA);

        assertThat(logReader.read(handler, 1), is(1));

        final InOrder inOrder = inOrder(logBuffer, stateBuffer, handler);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(0));
        inOrder.verify(handler).onData(eq(logBuffer), eq(HEADER_LENGTH), eq(msgLength), any(Header.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldReadMultipleMessages()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;
        final int alignedFrameLength = align(frameLength, FRAME_ALIGNMENT);

        when(logBuffer.getIntVolatile(lengthOffset(0))).thenReturn(frameLength);
        when(logBuffer.getIntVolatile(lengthOffset(alignedFrameLength))).thenReturn(frameLength);
        when(logBuffer.getShort(anyInt())).thenReturn((short)HDR_TYPE_DATA);

        assertThat(logReader.read(handler, Integer.MAX_VALUE), is(2));

        final InOrder inOrder = inOrder(logBuffer, stateBuffer, handler);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(0));
        inOrder.verify(handler).onData(eq(logBuffer), eq(HEADER_LENGTH), eq(msgLength), any(Header.class));

        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(alignedFrameLength));
        inOrder.verify(handler).onData(eq(logBuffer), eq(alignedFrameLength + HEADER_LENGTH), eq(msgLength), any(Header.class));
    }

    @Test
    public void shouldReadLastMessage()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;
        final int alignedFrameLength = align(frameLength, FRAME_ALIGNMENT);
        final int startOfMessage = LOG_BUFFER_CAPACITY - alignedFrameLength;

        when(logBuffer.getIntVolatile(lengthOffset(startOfMessage))).thenReturn(frameLength);
        when(logBuffer.getShort(typeOffset(startOfMessage))).thenReturn((short)HDR_TYPE_DATA);

        logReader.seek(startOfMessage);
        assertThat(logReader.read(handler, Integer.MAX_VALUE), is(1));
        assertTrue(logReader.isComplete());

        final InOrder inOrder = inOrder(logBuffer, stateBuffer, handler);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(startOfMessage));
        inOrder.verify(handler).onData(eq(logBuffer), eq(startOfMessage + HEADER_LENGTH), eq(msgLength), any(Header.class));
    }

    @Test
    public void shouldNotReadLastMessageWhenPadding()
    {
        final int msgLength = 1;
        final int frameLength = HEADER_LENGTH + msgLength;
        final int alignedFrameLength = align(frameLength, FRAME_ALIGNMENT);
        final int startOfMessage = LOG_BUFFER_CAPACITY - alignedFrameLength;

        when(logBuffer.getIntVolatile(lengthOffset(startOfMessage))).thenReturn(frameLength);
        when(logBuffer.getShort(typeOffset(startOfMessage))).thenReturn((short)PADDING_FRAME_TYPE);

        logReader.seek(startOfMessage);
        assertThat(logReader.read(handler, Integer.MAX_VALUE), is(0));
        assertTrue(logReader.isComplete());

        final InOrder inOrder = inOrder(logBuffer, stateBuffer);
        inOrder.verify(logBuffer).getIntVolatile(lengthOffset(startOfMessage));
        verify(handler, never()).onData(any(), anyInt(), anyInt(), any());
    }
}
