package io.github.xxyopen.novel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.xxyopen.novel.core.auth.UserHolder;
import io.github.xxyopen.novel.dao.entity.BookInfo;
import io.github.xxyopen.novel.dao.mapper.BookChapterMapper;
import io.github.xxyopen.novel.dao.mapper.BookContentMapper;
import io.github.xxyopen.novel.dao.mapper.BookInfoMapper;
import io.github.xxyopen.novel.dto.req.ChapterAddReqDto;
import io.github.xxyopen.novel.dto.resp.BookInfoRespDto;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.mq.AmqpMsgManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 小说模块 服务实现类测试
 */
@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    private static final Long BOOK_ID = 1L;

    private static final Long CATEGORY_ID = 10L;

    private static final Long AUTHOR_ID = 100L;

    @Mock
    private BookInfoCacheManager bookInfoCacheManager;

    @Mock
    private BookInfoMapper bookInfoMapper;

    @Mock
    private BookChapterMapper bookChapterMapper;

    @Mock
    private BookContentMapper bookContentMapper;

    @Mock
    private AmqpMsgManager amqpMsgManager;

    @InjectMocks
    private BookServiceImpl bookService;

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void listRecBooksWhenCategoryHasFewerBooksThanRecCount() {
        stubCategoryBooks(List.of(1L, 2L));

        List<BookInfoRespDto> books = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> bookService.listRecBooks(BOOK_ID).getData());

        assertThat(books).extracting(BookInfoRespDto::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void listRecBooksWhenCategoryHasNoBooks() {
        stubCategoryBooks(List.of());

        List<BookInfoRespDto> books = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> bookService.listRecBooks(BOOK_ID).getData());

        assertThat(books).isEmpty();
    }

    @Test
    void listRecBooksReturnsFourDistinctBooksOfCategory() {
        List<Long> categoryBookIds = LongStream.rangeClosed(1, 10).boxed().toList();
        stubCategoryBooks(categoryBookIds);

        List<BookInfoRespDto> books = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> bookService.listRecBooks(BOOK_ID).getData());

        assertThat(books).hasSize(4)
            .extracting(BookInfoRespDto::getId)
            .doesNotHaveDuplicates()
            .isSubsetOf(categoryBookIds);
    }

    @Test
    void saveBookChapterUpdatesBookUpdateTime() {
        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(BOOK_ID);
        bookInfo.setAuthorId(AUTHOR_ID);
        bookInfo.setWordCount(1000);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);
        UserHolder.setAuthorId(AUTHOR_ID);

        ChapterAddReqDto dto = new ChapterAddReqDto();
        dto.setBookId(BOOK_ID);
        dto.setChapterName("第一章");
        dto.setChapterContent("正文".repeat(30));
        dto.setIsVip(0);

        LocalDateTime beforeSave = LocalDateTime.now();
        bookService.saveBookChapter(dto);

        ArgumentCaptor<BookInfo> captor = ArgumentCaptor.forClass(BookInfo.class);
        verify(bookInfoMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUpdateTime()).isNotNull().isAfterOrEqualTo(beforeSave);
    }

    /**
     * 模拟当前小说所属分类下最近更新的小说ID列表（缓存返回的是不可变列表）
     */
    private void stubCategoryBooks(List<Long> categoryBookIds) {
        when(bookInfoCacheManager.getBookInfo(anyLong())).thenAnswer(
            invocation -> BookInfoRespDto.builder()
                .id(invocation.getArgument(0))
                .categoryId(CATEGORY_ID)
                .build());
        when(bookInfoCacheManager.getLastUpdateIdList(CATEGORY_ID)).thenReturn(categoryBookIds);
    }

}
