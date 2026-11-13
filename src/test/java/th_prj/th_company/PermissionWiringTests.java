package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.board.Board;
import th_prj.th_company.board.BoardRepository;
import th_prj.th_company.board.BoardType;
import th_prj.th_company.board.PostRepository;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;

/** 관리 권한(Permission)이 실제 기능에 적용되는지 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionWiringTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private BoardRepository boardRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String suffix;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 6);
	}

	private String user(String prefix, Permission... permissions) {
		String loginId = prefix + suffix;
		Member m = new Member(loginId, passwordEncoder.encode("pw"), prefix + "님", Role.USER);
		for (Permission p : permissions) {
			m.grant(p);
		}
		memberRepository.save(m);
		return loginId;
	}

	private MockHttpSession login(String loginId) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password("pw"))
			.andReturn().getRequest().getSession();
	}

	@Test
	void 일반사용자는_관리자_아이콘이_없고_어떤_관리자화면도_못_연다() throws Exception {
		MockHttpSession s = login(user("plain"));
		mvc.perform(get("/").session(s)).andExpect(content().string(org.hamcrest.Matchers.not(containsString("관리자 페이지"))));
		mvc.perform(get("/admin").session(s)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/boards").session(s)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/approval-templates").session(s)).andExpect(status().isForbidden());
		mvc.perform(get("/attendance/manage").session(s)).andExpect(status().isForbidden());
	}

	@Test
	void 게시판관리자는_게시판과_남의_글을_관리하지만_다른_관리자화면은_못_연다() throws Exception {
		MockHttpSession manager = login(user("bm", Permission.BOARD_MANAGER));
		mvc.perform(get("/").session(manager)).andExpect(content().string(containsString("관리자 페이지")));
		mvc.perform(get("/admin").session(manager)).andExpect(redirectedUrl("/admin/boards"));
		String html = mvc.perform(get("/admin/boards").session(manager))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		// 사이드바에는 자기 기능만
		assertThat(html).contains("게시판 관리").doesNotContain("사용자 관리", "권한 관리");
		mvc.perform(get("/admin/members").session(manager)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/approval-templates").session(manager)).andExpect(status().isForbidden());

		// 관리자만 글쓰기 게시판(공지)에 쓰고, 남의 글을 지울 수 있다
		Board notice = new Board(BoardType.BOARD, "공지" + suffix, null, 0);
		notice.update(notice.getName(), null, null, 0, true, true);
		notice = boardRepository.save(notice);
		Board free = boardRepository.save(new Board(BoardType.BOARD, "자유" + suffix, null, 1));

		mvc.perform(multipart("/boards/{b}/posts", notice.getId()).param("title", "공지글").param("content", "x")
				.session(manager).with(csrf()))
			.andExpect(status().is3xxRedirection());
		MockHttpSession writer = login(user("wr"));
		mvc.perform(multipart("/boards/{b}/posts", notice.getId()).param("title", "몰래").param("content", "x")
				.session(writer).with(csrf()))
			.andExpect(status().isForbidden());
		String location = mvc.perform(multipart("/boards/{b}/posts", free.getId()).param("title", "남의글").param("content", "x")
				.session(writer).with(csrf()))
			.andReturn().getResponse().getRedirectedUrl();
		Long postId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

		mvc.perform(post("/boards/{b}/posts/{p}/delete", free.getId(), postId).session(manager).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(postRepository.findById(postId)).isEmpty();
	}

	@Test
	void 일정관리자는_회사일정을_넣고_일반사용자는_못_넣는다() throws Exception {
		LocalDate day = LocalDate.of(2032, 3, 10);
		MockHttpSession manager = login(user("sm", Permission.SCHEDULE_MANAGER));
		mvc.perform(post("/schedule").session(manager).with(csrf())
				.param("type", "COMPANY").param("title", "창립기념일").param("startDate", day.toString())
				.param("endDate", day.toString()).param("allDay", "true"))
			.andExpect(status().is3xxRedirection());

		MockHttpSession plain = login(user("sp"));
		mvc.perform(post("/schedule").session(plain).with(csrf())
				.param("type", "COMPANY").param("title", "몰래 회사일정").param("startDate", day.toString())
				.param("endDate", day.toString()).param("allDay", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("alert-error")));
	}

	@Test
	void 근태관리자는_근태관리를_결재양식관리자는_양식관리를_연다() throws Exception {
		MockHttpSession am = login(user("am", Permission.ATTENDANCE_MANAGER));
		mvc.perform(get("/attendance/manage").session(am))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("전체 근태 현황")));
		mvc.perform(get("/admin").session(am)).andExpect(status().isForbidden());

		MockHttpSession tm = login(user("tm", Permission.APPROVAL_MANAGER));
		mvc.perform(get("/admin").session(tm)).andExpect(redirectedUrl("/admin/approval-templates"));
		mvc.perform(get("/admin/approval-templates").session(tm)).andExpect(status().isOk());
		mvc.perform(get("/admin/boards").session(tm)).andExpect(status().isForbidden());
	}
}
