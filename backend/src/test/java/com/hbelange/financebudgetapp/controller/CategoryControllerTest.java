package com.hbelange.financebudgetapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
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

import com.hbelange.financebudgetapp.dto.BudgetCategoryDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.service.CategoryService;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void getAll_returns200WithGroups() throws Exception {
        CategoryGroupDTO dto = new CategoryGroupDTO(GROUP_ID, "Housing", List.of());
        when(categoryService.findAllGroups(any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/category-groups").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()))
            .andExpect(jsonPath("$[0].name").value("Housing"));
    }

    @Test
    void createGroup_returns201() throws Exception {
        CategoryGroupDTO dto = new CategoryGroupDTO(GROUP_ID, "Housing", List.of());
        when(categoryService.createGroup(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/category-groups").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Housing\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(GROUP_ID.toString()));
    }

    @Test
    void createGroup_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/category-groups").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addCategory_returns201() throws Exception {
        BudgetCategoryDTO catView = new BudgetCategoryDTO(CAT_ID, "Rent");
        CategoryGroupDTO dto = new CategoryGroupDTO(GROUP_ID, "Housing", List.of(catView));
        when(categoryService.addCategory(eq(GROUP_ID), any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/category-groups/" + GROUP_ID + "/categories").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rent\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categories[0].name").value("Rent"));
    }

    @Test
    void addCategory_returns404_whenGroupMissing() throws Exception {
        when(categoryService.addCategory(eq(GROUP_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/category-groups/" + GROUP_ID + "/categories").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rent\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void reorderGroups_returns204() throws Exception {
        mockMvc.perform(patch("/api/category-groups/reorder").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"" + GROUP_ID + "\",\"sortOrder\":0}]"))
            .andExpect(status().isNoContent());

        verify(categoryService).reorderGroups(any(), any());
    }

    @Test
    void reorderCategories_returns204() throws Exception {
        mockMvc.perform(patch("/api/category-groups/" + GROUP_ID + "/categories/reorder").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"" + CAT_ID + "\",\"sortOrder\":0}]"))
            .andExpect(status().isNoContent());

        verify(categoryService).reorderCategories(any(), any());
    }

    @Test
    void renameGroup_returns200WithUpdatedGroup() throws Exception {
        CategoryGroupDTO dto = new CategoryGroupDTO(GROUP_ID, "New Name", List.of());
        when(categoryService.renameGroup(eq(GROUP_ID), any(), any())).thenReturn(dto);

        mockMvc.perform(put("/api/category-groups/" + GROUP_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void renameGroup_returns404_whenGroupMissing() throws Exception {
        when(categoryService.renameGroup(eq(GROUP_ID), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/category-groups/" + GROUP_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_returns204() throws Exception {
        mockMvc.perform(delete("/api/category-groups/" + GROUP_ID).with(jwt()))
            .andExpect(status().isNoContent());

        verify(categoryService).deleteGroup(eq(GROUP_ID), any());
    }

    @Test
    void deleteGroup_returns409_whenTransactionsExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Group contains categories with existing transactions"))
            .when(categoryService).deleteGroup(eq(GROUP_ID), any());

        mockMvc.perform(delete("/api/category-groups/" + GROUP_ID).with(jwt()))
            .andExpect(status().isConflict());
    }

    @Test
    void renameCategory_returns204() throws Exception {
        mockMvc.perform(put("/api/categories/" + CAT_ID).with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
            .andExpect(status().isNoContent());

        verify(categoryService).renameCategory(eq(CAT_ID), any(), any());
    }

    @Test
    void deleteCategory_returns204() throws Exception {
        mockMvc.perform(delete("/api/categories/" + CAT_ID).with(jwt()))
            .andExpect(status().isNoContent());

        verify(categoryService).deleteCategory(eq(CAT_ID), any());
    }

    @Test
    void deleteCategory_returns409_whenTransactionsExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Category has existing transactions"))
            .when(categoryService).deleteCategory(eq(CAT_ID), any());

        mockMvc.perform(delete("/api/categories/" + CAT_ID).with(jwt()))
            .andExpect(status().isConflict());
    }
}
