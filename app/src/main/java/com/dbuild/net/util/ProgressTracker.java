package com.dbuild.net.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProgressTracker {

    private String operationName;
    private int totalSteps;
    private int currentStep;
    private long startTime;
    private boolean running;
    private boolean completed;
    private String lastMessage;

    private final List<ProgressListener> listeners;

    public interface ProgressListener {
        void onProgressUpdate(int percent, String message);
        void onCompleted();
        void onError(String error);
    }

    public ProgressTracker() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentStep = 0;
        this.totalSteps = 0;
        this.running = false;
        this.completed = false;
        this.lastMessage = "";
    }

    public void start(String operationName, int totalSteps) {
        this.operationName = operationName;
        this.totalSteps = Math.max(1, totalSteps);
        this.currentStep = 0;
        this.startTime = System.currentTimeMillis();
        this.running = true;
        this.completed = false;
        this.lastMessage = "Started " + operationName;
        notifyProgressUpdate(0, lastMessage);
    }

    public void advance(int steps) {
        if (!running) return;
        currentStep = Math.min(currentStep + steps, totalSteps);
        lastMessage = "Step " + currentStep + " of " + totalSteps;
        notifyProgressUpdate(getPercent(), lastMessage);
        if (currentStep >= totalSteps) {
            complete();
        }
    }

    public void advance(String message) {
        if (!running) return;
        currentStep = Math.min(currentStep + 1, totalSteps);
        lastMessage = message;
        notifyProgressUpdate(getPercent(), lastMessage);
        if (currentStep >= totalSteps) {
            complete();
        }
    }

    public void setProgress(int step, String message) {
        if (!running) return;
        currentStep = Math.max(0, Math.min(step, totalSteps));
        lastMessage = message;
        notifyProgressUpdate(getPercent(), lastMessage);
        if (currentStep >= totalSteps) {
            complete();
        }
    }

    public int getPercent() {
        if (totalSteps <= 0) return 0;
        return Math.round((currentStep / (float) totalSteps) * 100);
    }

    public long getElapsedTime() {
        if (startTime == 0) return 0;
        return System.currentTimeMillis() - startTime;
    }

    public long getEstimatedTimeRemaining() {
        if (!running || currentStep <= 0 || currentStep >= totalSteps) {
            return 0;
        }
        long elapsed = getElapsedTime();
        float progressRatio = (float) currentStep / totalSteps;
        if (progressRatio <= 0) return 0;
        long estimatedTotal = (long) (elapsed / progressRatio);
        return Math.max(0, estimatedTotal - elapsed);
    }

    public void addListener(ProgressListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void complete() {
        if (!running) return;
        currentStep = totalSteps;
        running = false;
        completed = true;
        lastMessage = "Completed";
        notifyProgressUpdate(100, lastMessage);
        for (ProgressListener listener : listeners) {
            try {
                listener.onCompleted();
            } catch (Exception ignored) {
            }
        }
    }

    public void error(String message) {
        running = false;
        lastMessage = "Error: " + message;
        for (ProgressListener listener : listeners) {
            try {
                listener.onError(message);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void reset() {
        operationName = null;
        totalSteps = 0;
        currentStep = 0;
        startTime = 0;
        running = false;
        completed = false;
        lastMessage = "";
    }

    public String formatProgress() {
        int percent = getPercent();
        String message = lastMessage != null ? lastMessage : "";
        return percent + "% - " + message;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String formatElapsedTime() {
        long elapsed = getElapsedTime();
        return formatDuration(elapsed);
    }

    public String formatEstimatedTimeRemaining() {
        long remaining = getEstimatedTimeRemaining();
        return formatDuration(remaining);
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    private void notifyProgressUpdate(int percent, String message) {
        for (ProgressListener listener : listeners) {
            try {
                listener.onProgressUpdate(percent, message);
            } catch (Exception ignored) {
            }
        }
    }

    public void clearListeners() {
        listeners.clear();
    }

    public int getListenerCount() {
        return listeners.size();
    }

    public double getProgressRatio() {
        if (totalSteps <= 0) return 0.0;
        return (double) currentStep / totalSteps;
    }

    public String getProgressBarString(int barLength) {
        int percent = getPercent();
        int filled = (int) ((percent / 100.0) * barLength);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                sb.append('=');
            } else {
                sb.append(' ');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ProgressTracker{" +
                "operation='" + operationName + '\'' +
                ", progress=" + getPercent() + "%" +
                ", step=" + currentStep + "/" + totalSteps +
                ", running=" + running +
                ", message='" + lastMessage + '\'' +
                '}';
    }
}
