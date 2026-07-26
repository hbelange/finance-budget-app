package com.hbelange.financebudgetapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.GoalDTO;
import com.hbelange.financebudgetapp.enums.RolloverType;
import com.hbelange.financebudgetapp.repository.UserRepository;
import com.hbelange.financebudgetapp.service.GoalService;

@WebMvcTest(GoalController.class)
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoalService goalService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GOAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private String validRequestBody() {
        return "{\"categoryId\":\"" + CATEGORY_ID + "\",\"amount\":200.00,\"dayOfMonth\":15,\"rolloverType\":\"REFILL\"}";
    }

    @Test
    void createGoal_returns201WithDto() throws Exception {
        GoalDTO dto = new GoalDTO(GOAL_ID, CATEGORY_ID, new BigDecimal("200.00"), 15, RolloverType.REFILL);
        when(goalService.createGoal(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/goals").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(GOAL_ID.toString()))
            .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID.toString()))
            .andExpect(jsonPath("$.amount").value(200.00))
            .andExpect(jsonPath("$.dayOfMonth").value(15))
            .andExpect(jsonPath("$.rolloverType").value("REFILL"));
    }

    @Test
    void createGoal_returns400_whenCategoryIdMissing() throws Exception {
        mockMvc.perform(post("/api/goals").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200.00,\"dayOfMonth\":15,\"rolloverType\":\"REFILL\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createGoal_returns400_whenAmountNegative() throws Exception {
        mockMvc.perform(post("/api/goals").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + CATEGORY_ID + "\",\"amount\":-1,\"dayOfMonth\":15,\"rolloverType\":\"REFILL\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createGoal_returns400_whenDayOfMonthOutOfRange() throws Exception {
        mockMvc.perform(post("/api/goals").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + CATEGORY_ID + "\",\"amount\":200.00,\"dayOfMonth\":32,\"rolloverType\":\"REFILL\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createGoal_returns403_whenCategoryBelongsToOtherUser() throws Exception {
        when(goalService.createGoal(any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/goals").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    void getGoalByCategoryId_returns200WithDto() throws Exception {
        GoalDTO dto = new GoalDTO(GOAL_ID, CATEGORY_ID, new BigDecimal("200.00"), 15, RolloverType.REFILL);
        when(goalService.getGoalByCategoryId(eq(CATEGORY_ID), any())).thenReturn(dto);

        mockMvc.perform(get("/api/goals/" + CATEGORY_ID).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(GOAL_ID.toString()));
    }

    @Test
    void getGoalByCategoryId_returns404_whenNoGoalForCategory() throws Exception {
        when(goalService.getGoalByCategoryId(eq(CATEGORY_ID), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/goals/" + CATEGORY_ID).with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateGoal_returns200WithDto() throws Exception {
        GoalDTO dto = new GoalDTO(GOAL_ID, CATEGORY_ID, new BigDecimal("300.00"), 20, RolloverType.ACCUMULATE);
        when(goalService.updateGoal(eq(CATEGORY_ID), any(), any())).thenReturn(dto);

        mockMvc.perform(put("/api/goals/" + CATEGORY_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(300.00))
            .andExpect(jsonPath("$.rolloverType").value("ACCUMULATE"));
    }

    @Test
    void updateGoal_returns404_whenGoalMissing() throws Exception {
        when(goalService.updateGoal(eq(CATEGORY_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/goals/" + CATEGORY_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestBody()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateGoal_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/goals/" + CATEGORY_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + CATEGORY_ID + "\",\"amount\":200.00,\"dayOfMonth\":0,\"rolloverType\":\"REFILL\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteGoal_returns204() throws Exception {
        mockMvc.perform(delete("/api/goals/" + CATEGORY_ID).with(jwt()))
            .andExpect(status().isNoContent());

        verify(goalService).deleteGoal(eq(CATEGORY_ID), any());
    }

    @Test
    void deleteGoal_returns404_whenGoalMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
            .when(goalService).deleteGoal(eq(CATEGORY_ID), any());

        mockMvc.perform(delete("/api/goals/" + CATEGORY_ID).with(jwt()))
            .andExpect(status().isNotFound());
    }
}
