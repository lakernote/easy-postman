package com.laker.postman.performance.core.threadgroup;

import com.laker.postman.util.MessageKeys;

/**
 * 线程组数据模型，支持多种线程模式
 */
public class ThreadGroupData {
    public static final int MIN_THREADS = 1;
    public static final int DEFAULT_MAX_IN_FLIGHT_WAIT_SECONDS = 5;
    private static final int MIN_SECONDS = 1;
    private static final int MIN_LOOPS = 1;
    private static final int MIN_STEP = 1;

    // 线程组类型
    public enum ThreadMode {
        FIXED(MessageKeys.THREADGROUP_MODE_FIXED),           // 固定线程数
        RAMP_UP(MessageKeys.THREADGROUP_MODE_RAMP_UP),       // 递增线程数
        SPIKE(MessageKeys.THREADGROUP_MODE_SPIKE),           // 尖刺模式
        STAIRS(MessageKeys.THREADGROUP_MODE_STAIRS);         // 阶梯模式

        private final String messageKey;

        ThreadMode(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    // 公共属性
    public ThreadMode threadMode = ThreadMode.FIXED;  // 默认固定线程数
    public int numThreads = 20;                        // 固定模式-默认用户数
    public int duration = 60;                         // 所有模式-默认持续时间(秒)
    public int loops = 1;                             // 固定模式-默认循环次数
    public boolean useTime = true;                   // 是否使用时间而不是循环次数
    public int maxInFlightWaitSeconds = DEFAULT_MAX_IN_FLIGHT_WAIT_SECONDS; // 到时后等待在途请求完成的最长时间

    // 递增模式属性
    public int rampUpStartThreads = 1;                // 递增起始线程数
    public int rampUpEndThreads = 20;                 // 递增最终线程数
    public int rampUpTime = 30;                       // 递增时间(秒)
    public int rampUpDuration = 60;                  // 递增模式总测试持续时间(秒)

    // 尖刺模式属性
    public int spikeMinThreads = 1;                   // 尖刺最小线程数
    public int spikeMaxThreads = 20;                  // 尖刺最大线程数
    public int spikeRampUpTime = 20;                  // 尖刺上升时间(秒)
    public int spikeHoldTime = 15;                     // 尖刺保持时间(秒)
    public int spikeRampDownTime = 20;                // 尖刺下降时间(秒)
    public int spikeDuration = 60;                   // 尖刺模式总测试持续时间(秒)


    // 阶梯模式属性
    public int stairsStartThreads = 5;                // 阶梯起始线程数
    public int stairsEndThreads = 20;                 // 阶梯最终线程数
    public int stairsStep = 5;                        // 阶梯步长
    public int stairsHoldTime = 15;                   // 每阶段保持时间(秒)
    public int stairsDuration = 60;                  // 阶梯模式总测试持续时间(秒)

    public void normalize() {
        if (threadMode == null) {
            threadMode = ThreadMode.FIXED;
        }

        numThreads = atLeast(numThreads, MIN_THREADS);
        duration = atLeast(duration, MIN_SECONDS);
        loops = atLeast(loops, MIN_LOOPS);
        maxInFlightWaitSeconds = atLeast(maxInFlightWaitSeconds, MIN_SECONDS);

        rampUpStartThreads = atLeast(rampUpStartThreads, MIN_THREADS);
        rampUpEndThreads = atLeast(rampUpEndThreads, MIN_THREADS);
        if (rampUpStartThreads > rampUpEndThreads) {
            int previousStart = rampUpStartThreads;
            rampUpStartThreads = rampUpEndThreads;
            rampUpEndThreads = previousStart;
        }
        rampUpTime = atLeast(rampUpTime, MIN_SECONDS);
        rampUpDuration = atLeast(rampUpDuration, MIN_SECONDS);

        spikeMinThreads = atLeast(spikeMinThreads, MIN_THREADS);
        spikeMaxThreads = atLeast(spikeMaxThreads, MIN_THREADS);
        if (spikeMinThreads > spikeMaxThreads) {
            int previousMin = spikeMinThreads;
            spikeMinThreads = spikeMaxThreads;
            spikeMaxThreads = previousMin;
        }
        spikeRampUpTime = atLeast(spikeRampUpTime, MIN_SECONDS);
        spikeHoldTime = atLeast(spikeHoldTime, 0);
        spikeRampDownTime = atLeast(spikeRampDownTime, MIN_SECONDS);
        spikeDuration = atLeast(spikeDuration, MIN_SECONDS);

        stairsStartThreads = atLeast(stairsStartThreads, MIN_THREADS);
        stairsEndThreads = atLeast(stairsEndThreads, MIN_THREADS);
        if (stairsStartThreads > stairsEndThreads) {
            int previousStart = stairsStartThreads;
            stairsStartThreads = stairsEndThreads;
            stairsEndThreads = previousStart;
        }
        stairsStep = atLeast(stairsStep, MIN_STEP);
        stairsHoldTime = atLeast(stairsHoldTime, MIN_SECONDS);
        stairsDuration = atLeast(stairsDuration, MIN_SECONDS);
    }

    private static int atLeast(int value, int min) {
        return Math.max(min, value);
    }
}
