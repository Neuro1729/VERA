package com.example.entitlements.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BooleanValue.class, name = "BOOLEAN"),
        @JsonSubTypes.Type(value = QuantityValue.class, name = "QUANTITY"),
        @JsonSubTypes.Type(value = QuotaValue.class, name = "QUOTA"),
        @JsonSubTypes.Type(value = RangeValue.class, name = "RANGE"),
        @JsonSubTypes.Type(value = TimeRangeValue.class, name = "TIME_RANGE"),
        @JsonSubTypes.Type(value = SetValue.class, name = "SET"),
        @JsonSubTypes.Type(value = TextValue.class, name = "TEXT")
})
public sealed interface EntitlementValue permits BooleanValue, QuantityValue, QuotaValue, RangeValue, TimeRangeValue, SetValue, TextValue {
    EntitlementValueType valueType();
}
