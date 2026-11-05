package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.board.Attachment;
import th_prj.th_company.board.AttachmentRepository;
import th_prj.th_company.board.Board;
import th_prj.th_company.board.BoardRepository;
import th_prj.th_company.board.BoardType;
import th_prj.th_company.board.Post;
import th_prj.th_company.board.PostRepository;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;

@SpringBootTest
@AutoConfigureMockMvc
class BoardTests {

	@Autowired private MockMvc mvc;
	@Autowired private BoardRepository boardRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private AttachmentRepository attachmentRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private Board free;
	private Board notice;
	private String writer;
	private String other;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		Board folder = boardRepository.save(new Board(BoardType.FOLDER, "폴더" + suffix, null, 0));
		free = boardRepository.save(new Board(BoardType.BOARD, "자유" + suffix, folder, 10));
		notice = new Board(BoardType.BOARD, "공지" + suffix, folder, 0);
		notice.update(notice.getName(), null, folder, 0, true, true);
		notice = boardRepository.save(notice);

		writer = "writer" + suffix;
		other = "other" + suffix;
		memberRepository.save(new Member(writer, passwordEncoder.encode("pw"), "작성자" + suffix, Role.USER));
		memberRepository.save(new Member(other, passwordEncoder.encode("pw"), "다른사람" + suffix, Role.USER));
	}

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andReturn().getRequest().getSession();
	}

	private Long write(MockHttpSession session, Board board, String title, MockMultipartFile... files) throws Exception {
		var request = multipart("/boards/{b}/posts", board.getId());
		for (MockMultipartFile f : files) {
			request.file(f);
		}
		String location = mvc.perform(request.param("title", title).param("content", "본문 " + title)
				.session(session).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	@Test
	void userWritesPostWithAttachment_listsViewsAndDownloads() throws Exception {
		MockHttpSession session = login(writer, "pw");
		byte[] bytes = "첨부 내용".getBytes(StandardCharsets.UTF_8);
		Long postId = write(session, free, "첫 글",
			new MockMultipartFile("uploads", "보고서.txt", "text/plain", bytes));

		mvc.perform(get("/boards/{b}", free.getId()).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("첫 글")));

		mvc.perform(get("/boards/{b}/posts/{p}", free.getId(), postId).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("본문 첫 글")))
			.andExpect(content().string(containsString("보고서.txt")));
		assertThat(postRepository.findById(postId).orElseThrow().getViewCount()).isEqualTo(1);

		Attachment file = attachmentRepository.findByPostIdOrderById(postId).get(0);
		mvc.perform(get("/attachments/{id}", file.getId()).session(session))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("UTF-8''")))
			.andExpect(content().bytes(bytes));
	}

	@Test
	void onlyAuthorOrAdminCanEditOrDelete_andFilesAreRemoved() throws Exception {
		Long postId = write(login(writer, "pw"), free, "남의 글",
			new MockMultipartFile("uploads", "a.txt", "text/plain", "x".getBytes()));
		Attachment file = attachmentRepository.findByPostIdOrderById(postId).get(0);
		Path stored = Path.of("build/test-uploads").resolve(file.getStoredName());
		assertThat(Files.exists(stored)).isTrue();

		MockHttpSession otherSession = login(other, "pw");
		mvc.perform(get("/boards/{b}/posts/{p}/edit", free.getId(), postId).session(otherSession))
			.andExpect(status().isForbidden());
		mvc.perform(post("/boards/{b}/posts/{p}/delete", free.getId(), postId).session(otherSession).with(csrf()))
			.andExpect(status().isForbidden());

		mvc.perform(post("/boards/{b}/posts/{p}/delete", free.getId(), postId).session(login("sysadmin", "a")).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(postRepository.findById(postId)).isEmpty();
		assertThat(Files.exists(stored)).isFalse();
	}

	@Test
	void authorCanEditAndRemoveAttachment() throws Exception {
		MockHttpSession session = login(writer, "pw");
		Long postId = write(session, free, "수정 전",
			new MockMultipartFile("uploads", "old.txt", "text/plain", "old".getBytes()));
		Long fileId = attachmentRepository.findByPostIdOrderById(postId).get(0).getId();

		mvc.perform(multipart("/boards/{b}/posts/{p}", free.getId(), postId)
				.file(new MockMultipartFile("uploads", "new.txt", "text/plain", "new".getBytes()))
				.param("title", "수정 후").param("content", "바뀐 본문")
				.param("deleteAttachmentIds", fileId.toString())
				.session(session).with(csrf()))
			.andExpect(status().is3xxRedirection());

		Post post = postRepository.findById(postId).orElseThrow();
		assertThat(post.getTitle()).isEqualTo("수정 후");
		assertThat(attachmentRepository.findByPostIdOrderById(postId))
			.extracting(Attachment::getOriginalName).containsExactly("new.txt");
	}

	@Test
	void noticeBoardIsAdminOnly_andPinnedPostsShowOnHome() throws Exception {
		MockHttpSession user = login(writer, "pw");
		mvc.perform(get("/boards/{b}/posts/new", notice.getId()).session(user)).andExpect(status().isForbidden());
		mvc.perform(multipart("/boards/{b}/posts", notice.getId()).param("title", "몰래").param("content", "x")
				.session(user).with(csrf()))
			.andExpect(status().isForbidden());

		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(multipart("/boards/{b}/posts", notice.getId()).param("title", "전사 공지 안내")
				.param("content", "내용").param("pinned", "true").session(admin).with(csrf()))
			.andExpect(status().is3xxRedirection());

		mvc.perform(get("/boards/{b}", notice.getId()).session(user))
			.andExpect(content().string(containsString("tag-notice")));
		mvc.perform(get("/").session(user))
			.andExpect(content().string(containsString("전사 공지 안내")));
	}

	@Test
	void commentsCanBeDeletedOnlyByAuthor() throws Exception {
		MockHttpSession writerSession = login(writer, "pw");
		Long postId = write(writerSession, free, "댓글 테스트");

		MockHttpSession otherSession = login(other, "pw");
		mvc.perform(post("/boards/{b}/posts/{p}/comments", free.getId(), postId)
				.param("content", "좋은 글이네요").session(otherSession).with(csrf()))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/boards/{b}/posts/{p}/comments", free.getId(), postId)
				.param("content", "  ").session(otherSession).with(csrf()))
			.andExpect(flash().attribute("error", "댓글 내용을 입력하세요."));

		String html = mvc.perform(get("/boards/{b}/posts/{p}", free.getId(), postId).session(writerSession))
			.andExpect(content().string(containsString("좋은 글이네요")))
			.andReturn().getResponse().getContentAsString();
		String marker = "/comments/";
		int i = html.indexOf(marker);
		assertThat(i).as("작성자 화면에는 남의 댓글 삭제 버튼이 없어야 함").isEqualTo(-1);

		Long commentId = Long.valueOf(mvc.perform(get("/boards/{b}/posts/{p}", free.getId(), postId).session(otherSession))
			.andReturn().getResponse().getContentAsString()
			.replaceAll("(?s).*/comments/(\\d+)/delete.*", "$1"));
		mvc.perform(post("/boards/{b}/posts/{p}/comments/{c}/delete", free.getId(), postId, commentId)
				.session(writerSession).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/boards/{b}/posts/{p}/comments/{c}/delete", free.getId(), postId, commentId)
				.session(otherSession).with(csrf()))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	void searchAndPaging() throws Exception {
		MockHttpSession session = login(writer, "pw");
		for (int i = 1; i <= 17; i++) {
			write(session, free, "페이지글" + i);
		}
		write(session, free, "특별한 제목");

		mvc.perform(get("/boards/{b}", free.getId()).param("page", "2").session(session))
			.andExpect(content().string(containsString("페이지글3")))
			.andExpect(content().string(not(containsString("페이지글17"))));

		mvc.perform(get("/boards/{b}", free.getId()).param("field", "title").param("q", "특별").session(session))
			.andExpect(content().string(containsString("특별한 제목")))
			.andExpect(content().string(not(containsString("페이지글1<"))));
	}

	@Test
	void hiddenBoardIsNotFound_andAdminBoardRulesApply() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		free.update(free.getName(), null, free.getParent(), 10, false, false);
		boardRepository.save(free);
		mvc.perform(get("/boards/{b}", free.getId()).session(admin)).andExpect(status().isNotFound());

		// 게시판 밑에는 게시판을 둘 수 없음
		mvc.perform(post("/admin/boards").session(admin).with(csrf())
				.param("type", "BOARD").param("name", "하위").param("parentId", notice.getId().toString())
				.param("sortOrder", "0"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("상위 항목은 폴더만")));

		// 글이 있는 게시판은 삭제 불가
		Long postId = write(admin, notice, "삭제 막기");
		mvc.perform(post("/admin/boards/{id}/delete", notice.getId()).session(admin).with(csrf()))
			.andExpect(flash().attribute("error", containsString("글이 있는 게시판")));
		assertThat(postRepository.findById(postId)).isPresent();
	}
}
