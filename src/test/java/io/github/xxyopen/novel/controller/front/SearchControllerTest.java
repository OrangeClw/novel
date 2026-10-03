package io.github.xxyopen.novel.controller.front;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.exception.CommonExceptionHandler;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.BookSearchReqDto;
import io.github.xxyopen.novel.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 小说搜索接口 排序参数校验测试
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private static final String SEARCH_URL = ApiRouterConsts.API_FRONT_SEARCH_URL_PREFIX + "/books";

    @Mock
    private SearchService searchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
            .setControllerAdvice(new CommonExceptionHandler())
            .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"last_chapter_update_time", "word_count", "visit_count"})
    void searchBooksWithAllowedSort(String sort) throws Exception {
        when(searchService.searchBooks(any())).thenReturn(
            RestResp.ok(PageRespDto.of(1, 10, 0, List.of())));

        mockMvc.perform(get(SEARCH_URL).param("sort", sort))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCodeEnum.OK.getCode()));

        ArgumentCaptor<BookSearchReqDto> captor = ArgumentCaptor.forClass(BookSearchReqDto.class);
        verify(searchService).searchBooks(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(sort);
    }

    @Test
    void searchBooksWithoutSort() throws Exception {
        when(searchService.searchBooks(any())).thenReturn(
            RestResp.ok(PageRespDto.of(1, 10, 0, List.of())));

        mockMvc.perform(get(SEARCH_URL).param("keyword", "斗破"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCodeEnum.OK.getCode()));

        verify(searchService).searchBooks(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(select sleep(3))",
        "if(1=1,word_count,visit_count)",
        "word_count,(select 1)",
        "visit_count;drop table book_info",
        "id",
        ""
    })
    void searchBooksWithIllegalSortIsRejected(String sort) throws Exception {
        mockMvc.perform(get(SEARCH_URL).param("sort", sort))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ErrorCodeEnum.USER_REQUEST_PARAM_ERROR.getCode()));

        verifyNoInteractions(searchService);
    }

}
