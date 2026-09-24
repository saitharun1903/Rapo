package com.rideflow.entity;

/** Whether a completed trip's distance was measured from GPS track points or fell back to the estimate. */
public enum DistanceSource {
    TRACKED,
    ESTIMATED
}
