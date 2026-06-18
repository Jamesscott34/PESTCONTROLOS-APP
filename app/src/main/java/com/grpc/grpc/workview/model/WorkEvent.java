/**
 * WorkEvent.java
 *
 * Data class representing a work event in the calendar system.
 * Contains all necessary information for displaying and managing
 * calendar events including contracts, jobs, and follow-ups.
 *
 * Author: GRPC
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc.workview.model;

import java.util.Date;

public class WorkEvent {
    private String id;
    private String userName;
    private String eventType; // "contract", "job", "followup"
    private String eventId; // ID of the original contract/job
    private String eventName;
    private String date;
    private String time;
    // Optional end time for variable-length jobs/contracts (e.g. 08:30 - 15:00).
    // Existing code continues to use 'time' (start time) for scheduling.
    private String endTime;
    private String status; // "scheduled", "completed", "cancelled"
    private Date createdAt;
    private Date completedAt;
    private String address;
    private String issue;
    private String notes;
    private String createdBy;

    // Default constructor for Firebase
    public WorkEvent() {}

    // Constructor with all fields
    public WorkEvent(String userName, String eventType, String eventId, String eventName,
                    String date, String time, String status) {
        this.userName = userName;
        this.eventType = eventType;
        this.eventId = eventId;
        this.eventName = eventName;
        this.date = date;
        this.time = time;
        this.status = status;
        this.createdAt = new Date();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Get display text for the event
     */
    public String getDisplayText() {
        String statusText = "";
        switch (status) {
            case "scheduled":
                statusText = "⏰";
                break;
            case "completed":
                statusText = "✅";
                break;
            case "cancelled":
                statusText = "❌";
                break;
        }

        return statusText + " " + time + " - " + eventName;
    }

    /**
     * Get event type display text
     */
    public String getEventTypeDisplay() {
        switch (eventType) {
            case "contract":
                return "Contract";
            case "job":
                return "Job";
            case "followup":
                return "Follow-up";
            default:
                return "Event";
        }
    }
}
