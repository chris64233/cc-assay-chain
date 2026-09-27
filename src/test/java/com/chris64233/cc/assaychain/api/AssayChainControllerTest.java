package com.chris64233.cc.assaychain.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssayChainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullChainOverHttp() throws Exception {
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalNo":"WEB-1","miningArea":"甲玛","mass":100.0000,"custodian":"地勘院"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalNo").value("WEB-1"))
                .andExpect(jsonPath("$.leaf").value(true));

        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalNo":"WEB-1","miningArea":"甲玛","mass":50.0000,"custodian":"地勘院"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalNo":"WEB-BAD","miningArea":"甲玛","mass":-1.0000,"custodian":"地勘院"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/splits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-S1","parentExternalNo":"WEB-1","declaredLossMass":10.0000,
                                 "children":[
                                   {"externalNo":"WEB-1-A","mass":60.0000,"custodian":"地勘院"},
                                   {"externalNo":"WEB-1-B","mass":30.0000,"custodian":"地勘院"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lossMass").value(10.0000));

        mockMvc.perform(post("/api/splits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-S1","parentExternalNo":"WEB-1","declaredLossMass":99.0000,
                                 "children":[
                                   {"externalNo":"WEB-1-A","mass":60.0000,"custodian":"地勘院"},
                                   {"externalNo":"WEB-1-B","mass":30.0000,"custodian":"地勘院"}]}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/custody/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-C1","sampleExternalNo":"WEB-1-A",
                                 "fromCustodian":"地勘院","toLab":"中心实验室"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/custody/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-C1","confirmedBy":"南方实验室"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/custody/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-C1","confirmedBy":"中心实验室"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/assays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-R1","sampleExternalNo":"WEB-1-A","itemCode":"AU_GRADE",
                                 "resultValue":3.25,"unit":"g/t","submittedBy":"中心实验室"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.versionNo").value(1));

        // 复核人不能与提交人相同
        mockMvc.perform(post("/api/assays/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalNo":"WEB-RV1","resultEventNo":"WEB-R1",
                                 "decision":"APPROVE","decidedBy":"中心实验室"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/assays/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalNo":"WEB-RV1","resultEventNo":"WEB-R1",
                                 "decision":"APPROVE","decidedBy":"质量负责人"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SUBMISSION_REVIEW"))
                .andExpect(jsonPath("$.decision").value("APPROVE"));

        mockMvc.perform(get("/api/samples/WEB-1-A/results/AU_GRADE/effective"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.eventNo").value("WEB-R1"));

        mockMvc.perform(post("/api/assays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-R2","sampleExternalNo":"WEB-1-A","itemCode":"AU_GRADE",
                                 "resultValue":9.99,"unit":"g/t","submittedBy":"中心实验室"}
                                """))
                .andExpect(status().isConflict());

        // 生效结果只能通过更正申请修改
        mockMvc.perform(post("/api/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionNo":"WEB-C1","resultEventNo":"WEB-R1","newValue":3.30,
                                 "newUnit":"g/t","reason":"仪器标定错误",
                                 "evidence":"复检报告 LAB-2026-WEB1","requestedBy":"中心实验室"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.oldValue").value(3.250000));

        // 同号异内容冲突
        mockMvc.perform(post("/api/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionNo":"WEB-C1","resultEventNo":"WEB-R1","newValue":9.99,
                                 "newUnit":"g/t","reason":"仪器标定错误",
                                 "evidence":"复检报告 LAB-2026-WEB1","requestedBy":"中心实验室"}
                                """))
                .andExpect(status().isConflict());

        // 审批人必须与申请人不同
        mockMvc.perform(post("/api/corrections/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalNo":"WEB-CA1","correctionNo":"WEB-C1",
                                 "decision":"APPROVE","decidedBy":"中心实验室"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/corrections/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalNo":"WEB-CA1","correctionNo":"WEB-C1",
                                 "decision":"APPROVE","decidedBy":"技术负责人"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newEventNo").value("COR-WEB-C1"));

        mockMvc.perform(get("/api/samples/WEB-1-A/results/AU_GRADE/effective"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventNo").value("COR-WEB-C1"))
                .andExpect(jsonPath("$.versionNo").value(2));

        mockMvc.perform(get("/api/samples/WEB-1-A/results/AU_GRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.versions[1].sourceCorrectionNo").value("WEB-C1"))
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.effectiveEventNoAsOf").doesNotExist());

        mockMvc.perform(get("/api/samples/WEB-1-A/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineage.origin.externalNo").value("WEB-1-A"))
                .andExpect(jsonPath("$.results.length()").value(1));

        mockMvc.perform(get("/api/samples/WEB-1/lineage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.externalNo").value("WEB-1"))
                .andExpect(jsonPath("$.descendants.length()").value(2))
                .andExpect(jsonPath("$.timeline.length()").value(11));

        mockMvc.perform(get("/api/samples/NO-SUCH"))
                .andExpect(status().isNotFound());
    }
}
