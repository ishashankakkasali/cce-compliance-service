package org.openphc.cce.compliance.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * CloudEvents v1.0 envelope for CCE event messages.
 * Used as the Kafka message value for all CCE topics.
 *
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 */
public class CloudEventMessage {

    /** CloudEvents required attributes */
    private String id;
    private String source;
    private String type;

    @JsonProperty("specversion")
    private String specVersion = "1.0";

    /** CloudEvents optional attributes */
    private String subject;
    private OffsetDateTime time;

    @JsonProperty("datacontenttype")
    private String dataContentType = "application/json";

    /** CloudEvents data payload */
    private Map<String, Object> data;

    /** CCE extension attributes */
    @JsonProperty("correlationid")
    private String correlationId;

    @JsonProperty("sourceeventid")
    private String sourceEventId;

    @JsonProperty("protocolinstanceid")
    private String protocolInstanceId;

    @JsonProperty("protocoldefinitionid")
    private String protocolDefinitionId;

    @JsonProperty("actionid")
    private String actionId;

    @JsonProperty("facilityid")
    private String facilityId;

    public CloudEventMessage() {
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
    }

    public String getDataContentType() {
        return dataContentType;
    }

    public void setDataContentType(String dataContentType) {
        this.dataContentType = dataContentType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getProtocolInstanceId() {
        return protocolInstanceId;
    }

    public void setProtocolInstanceId(String protocolInstanceId) {
        this.protocolInstanceId = protocolInstanceId;
    }

    public String getProtocolDefinitionId() {
        return protocolDefinitionId;
    }

    public void setProtocolDefinitionId(String protocolDefinitionId) {
        this.protocolDefinitionId = protocolDefinitionId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }
}
