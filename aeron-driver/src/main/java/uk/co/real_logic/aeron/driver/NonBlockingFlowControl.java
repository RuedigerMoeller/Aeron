package uk.co.real_logic.aeron.driver;

import java.net.InetSocketAddress;

/**
 * Never blocks a sender regardless of subscriber/receiver state. This can be useful for
 * multicast senders. Slow subscribers need to drop in case.
 */

public class NonBlockingFlowControl implements SenderFlowControl {

    int x = 0; // debug
    @Override
    public long onStatusMessage(int termId, int completedTermOffset, int receiverWindowSize, InetSocketAddress address) {
        return -1;
    }

    @Override
    public long initialPositionLimit(int initialTermId, int termBufferCapacity) {
        return Long.MAX_VALUE;
    }
}
