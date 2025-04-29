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

//    private final WriteApi writeApi;
//    private final QueryApi queryApi;
//
//    @Value("${influx.bucket}")
//    private String bucket;
//
//    public InfluxService(WriteApi writeApi, QueryApi queryApi) {
//        this.writeApi = writeApi;
//        this.queryApi = queryApi;
//    }

    /** 단일 포인트 쓰기 */
    public void writeMeasurement(String commandId,
                                 String controlCode,
                                 String codeValue,
                                 String controlCodeName,
                                 String userId,
                                 String deviceId,
                                 String codeType,
                                 String commandFlow) {
//        Point point = Point
//                .measurement("command_log")
//                .addTag("COMD_ID", commandId)
//                .addTag("CTRL_CD", controlCode)
//                .addTag("CD_VALUE", codeValue)
//                .addTag("CTRL_CD_NM", controlCodeName)
//                .addTag("USER_ID", userId)
//                .addTag("DEVC_ID", deviceId)
//                .addField("CD_TYPE", Integer.parseInt(codeType))
//                .addField("COMD_FLOW", Integer.parseInt(commandFlow))
//                .time(Instant.now(), WritePrecision.NS);
//        writeApi.writePoint(point);
    }
}