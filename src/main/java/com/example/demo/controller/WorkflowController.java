package com.example.demo.controller;

import com.example.demo.controller.representation.ChangeExecutionRepresentation;
import com.example.demo.controller.representation.SetExecutionRepresentation;
import com.example.demo.controller.representation.StartWorkflowRepresentation;
import com.example.demo.model.Schedule;
import com.example.demo.model.Step;
import com.example.demo.model.Task;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @PostMapping(path = "/create")
    public String create(@RequestBody Schedule schedule) {
        log.info("create called");

        String workflowId = String.format("schedule_%s", UUID.randomUUID().toString());

        BpmnModelInstance bpmnModelInstance = buildBpmnModelInstance(schedule, workflowId);

        repositoryService
                .createDeployment()
                .addModelInstance("Novo bpm.bpmn", bpmnModelInstance)
                .enableDuplicateFiltering(true)
                .deploy();

        return workflowId;
    }

    private BpmnModelInstance buildBpmnModelInstance(@RequestBody Schedule schedule, String workflowId) {
        AbstractFlowNodeBuilder builder = Bpmn.createExecutableProcess(workflowId)
                .startEvent();

        for (Step step : schedule.getSteps()) {
            builder = builder
                    .subProcess(step.getName())
                    .embeddedSubProcess()
                    .startEvent();
            for (Task task : step.getTasks()) {
                builder = builder.userTask(task.getName());
            }
            builder = builder.endEvent()
                    .subProcessDone();
        }
        return builder.endEvent().done();
    }

    @PostMapping(path = "/{id}/start")
    public void startWorkflow(@PathVariable("id") String id, @RequestBody StartWorkflowRepresentation startWorkflowRepresentation) {
        runtimeService.createProcessInstanceByKey(id).businessKey(startWorkflowRepresentation.getBusinessKey()).execute();
    }

    @PostMapping(path = "/{id}/set-execution")
    public void setExecution(@PathVariable("id") String id, @RequestBody SetExecutionRepresentation representation) {

        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey(id)
                .processInstanceBusinessKey(representation.getBusinessKey())
                .singleResult();

        ActivityInstance activityInstance = runtimeService.getActivityInstance(processInstance.getId());

        runtimeService.createProcessInstanceModification(processInstance.getId())
                .cancelActivityInstance(activityInstance.getId())
                .startBeforeActivity(representation.getTaskId())
                .execute();
    }

    @PostMapping(path = "/{id}/change-execution")
    public void change(@PathVariable("id") String id, @RequestBody ChangeExecutionRepresentation representation) {

        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey(id)
                .processInstanceBusinessKey(representation.getBusinessKey())
                .singleResult();

        List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstance.getId());

        BpmnModelInstance bpmnModelInstance = buildBpmnModelInstance(representation.getSchedule(), id);

        String newDeploymentId = repositoryService
                .createDeployment()
                .addModelInstance("Novo bpm.bpmn", bpmnModelInstance)
                .enableDuplicateFiltering(true)
                .deploy()
                .getId();

        ProcessDefinition newProcessDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(newDeploymentId).singleResult();

        MigrationPlan migrationPlan = runtimeService
                .createMigrationPlan(processInstance.getProcessDefinitionId(), newProcessDefinition.getId())
                .mapActivities(activeActivityIds.get(0), representation.getTaskId())
                .build();

        runtimeService.newMigration(migrationPlan).processInstanceIds(processInstance.getId()).execute();
    }

}
