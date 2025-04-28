package com.oauth.service.impl;

import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class InfluxService {

    private final WriteApi writeApi;
    private final QueryApi queryApi;

    @Value("${influx.bucket}")
    private String bucket;

    public InfluxService(WriteApi writeApi, QueryApi queryApi) {
        this.writeApi = writeApi;
        this.queryApi = queryApi;
    }

    /** 단일 포인트 쓰기 */
    public void writeMeasurement() {
        Point point = Point
                .measurement("command_log")  // 고정 Measurement
                .addTag("COMD_ID", "ModeChange")  // COMD_ID를 Tag로 이동
                .addTag("CTRL_CD", "opMd")
                .addTag("CTRL_CD_NM", "모드 변경")
                .addTag("USER_ID", "yohan1202")
                .addTag("DEVC_ID", "0.2.48.1.1.1.204443326a33342d3253.20202020443846313542303130423243")
                .addField("CD_TYPE", 2)
                .addField("COMD_FLOW", 2)
                .time(Instant.now(), WritePrecision.NS);
        writeApi.writePoint(point);
    }
}