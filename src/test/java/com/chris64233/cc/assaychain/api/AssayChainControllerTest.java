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
}
