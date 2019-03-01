package com.example.demo.controller.representation;

import com.example.demo.model.Schedule;
import lombok.Getter;

@Getter
public class ChangeExecutionRepresentation {

    private Schedule schedule;
    private String taskId;
    private String businessKey;
}
