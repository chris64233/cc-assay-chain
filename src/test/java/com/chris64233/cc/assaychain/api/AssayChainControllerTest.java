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
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/assays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-R2","sampleExternalNo":"WEB-1-A","itemCode":"AU_GRADE",
                                 "resultValue":9.99,"unit":"g/t","submittedBy":"中心实验室"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/samples/WEB-1/lineage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.externalNo").value("WEB-1"))
                .andExpect(jsonPath("$.descendants.length()").value(2))
                .andExpect(jsonPath("$.timeline.length()").value(7));

        mockMvc.perform(get("/api/samples/NO-SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewAndCorrectionFlowOverHttp() throws Exception {
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalNo":"WEB-2","miningArea":"甲玛","mass":40.0000,"custodian":"中心实验室"}
                                """))
                .andExpect(status().isCreated());

        // 提交后待复核，尚无对外有效结果
        mockMvc.perform(post("/api/assays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-2-R1","sampleExternalNo":"WEB-2","itemCode":"AU_GRADE",
                                 "resultValue":2.35,"unit":"g/t","submittedBy":"中心实验室"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/results/current?sampleExternalNo=WEB-2&itemCode=AU_GRADE"))
                .andExpect(status().isNotFound());

        // 提交人不能自审
        mockMvc.perform(post("/api/results/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-2-AP0","resultEventNo":"WEB-2-R1",
                                 "decision":"APPROVED","reviewedBy":"中心实验室"}
                                """))
                .andExpect(status().isBadRequest());

        // 他人复核通过
        mockMvc.perform(post("/api/results/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-2-AP1","resultEventNo":"WEB-2-R1",
                                 "decision":"APPROVED","reviewedBy":"质量主管","comment":"合格"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVED"));

        mockMvc.perform(get("/api/results/current?sampleExternalNo=WEB-2&itemCode=AU_GRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultEventNo").value("WEB-2-R1"))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        // 生效结果不能再直接提交覆盖
        mockMvc.perform(post("/api/assays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-2-RX","sampleExternalNo":"WEB-2","itemCode":"AU_GRADE",
                                 "resultValue":9.99,"unit":"g/t","submittedBy":"中心实验室"}
                                """))
                .andExpect(status().isConflict());

        // 发起更正（仪器错误），审批通过产生新版本
        mockMvc.perform(post("/api/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correctionNo":"WEB-2-CO1","resultEventNo":"WEB-2-R1",
                                 "newResultEventNo":"WEB-2-R2","newResultValue":3.25,"newUnit":"g/t",
                                 "reason":"仪器校准错误","evidence":"校准记录 CERT-77",
                                 "requestedBy":"中心实验室"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.oldValue").value(2.350000));

        mockMvc.perform(post("/api/corrections/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventNo":"WEB-2-AP2","correctionNo":"WEB-2-CO1",
                                 "decision":"APPROVED","reviewedBy":"技术负责人"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultVersion.eventNo").value("WEB-2-R2"));

        // 当前有效结果切到 v2
        mockMvc.perform(get("/api/results/current?sampleExternalNo=WEB-2&itemCode=AU_GRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultEventNo").value("WEB-2-R2"))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.prevResultEventNo").value("WEB-2-R1"));

        // 版本链 + 复核记录联合查询
        mockMvc.perform(get("/api/results/history?sampleExternalNo=WEB-2&itemCode=AU_GRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].kind").value("RESULT_REVIEW"))
                .andExpect(jsonPath("$.reviews[1].kind").value("CORRECTION_REVIEW"));

        // 时点还原
        String switchAt = assaySwitchInstant();
        mockMvc.perform(get("/api/results/effective-at")
                        .param("sampleExternalNo", "WEB-2")
                        .param("itemCode", "AU_GRADE")
                        .param("at", switchAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultEventNo").value("WEB-2-R2"));

        mockMvc.perform(get("/api/results/effective-at")
                        .param("sampleExternalNo", "WEB-2")
                        .param("itemCode", "AU_GRADE")
                        .param("at", "2000-01-01T00:00:00Z"))
                .andExpect(status().isNotFound());

        // 非法时点格式
        mockMvc.perform(get("/api/results/effective-at")
                        .param("sampleExternalNo", "WEB-2")
                        .param("itemCode", "AU_GRADE")
                        .param("at", "not-a-time"))
                .andExpect(status().isBadRequest());

        // 谱系时间线包含复核与更正事件
        mockMvc.perform(get("/api/samples/WEB-2/lineage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[?(@.type=='RESULT_REVIEW')]").exists())
                .andExpect(jsonPath("$.timeline[?(@.type=='CORRECTION')]").exists())
                .andExpect(jsonPath("$.timeline[?(@.type=='CORRECTION_REVIEW')]").exists());
    }

    /** 取 v2 生效时间（即 v1 失效时间），用 history 接口并直接从 current 拿不到，改为极近未来。 */
    private String assaySwitchInstant() {
        // 切换瞬间及之后归属新版本；用未来 1 小时保证落在 v2 生效区间
        return java.time.Instant.now().plusSeconds(3600).toString();
    }
}
