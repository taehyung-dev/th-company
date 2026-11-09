package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import th_prj.th_company.admin.AdminMenuService;
import th_prj.th_company.admin.AdminSection;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.menu.MainMenu;
import th_prj.th_company.menu.MenuService;

@SpringBootTest
@AutoConfigureMockMvc
class MenuTests {

	@Autowired private MockMvc mvc;
	@Autowired private MenuService menuService;
	@Autowired private AdminMenuService adminMenuService;
	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	@AfterEach
	void restore() {
		menuService.reset();
		adminMenuService.reset();
	}

	private MockHttpSession login(String id, String pw) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(id).password(pw))
			.andReturn().getRequest().getSession();
	}

	private static MockHttpServletRequestBuilder save(MockHttpSession session, String... menus) {
		return post("/admin/menus").session(session).with(csrf()).param("menus", menus);
	}

	/** 화면 상단 메뉴 바의 글자를 순서대로 */
	private static String menubar(String html) {
		String bar = html.substring(html.indexOf(">", html.indexOf("class=\"menubar\"")) + 1);
		return bar.substring(0, bar.indexOf("</nav>")).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
	}

	@Test
	void defaultOrder_includesAddressBook() throws Exception {
		assertThat(menuService.ordered()).containsExactly(MainMenu.values());
		String html = mvc.perform(get("/").session(login("sysadmin", "a"))).andReturn().getResponse().getContentAsString();
		assertThat(menubar(html)).isEqualTo("홈 게시판 전자결재 일정 회의실예약 자원예약 근태관리 주소록");
	}

	@Test
	void topbarIcons_orgChartOpensAsPopup_adminIconOnlyForAdmins() throws Exception {
		String adminHtml = mvc.perform(get("/").session(login("sysadmin", "a"))).andReturn().getResponse().getContentAsString();
		assertThat(adminHtml)
			.contains("aria-label=\"조직도\"", "data-org-popup", "aria-label=\"관리자 페이지\"", "aria-label=\"로그아웃\"");
		// 관리자 화면 상단도 아이콘 버튼 (홈으로, 로그아웃)
		String adminPage = mvc.perform(get("/admin/menus").session(login("sysadmin", "a"))).andReturn().getResponse().getContentAsString();
		assertThat(adminPage).contains("class=\"icon-btn\" href=\"/\" title=\"홈으로\"", "aria-label=\"로그아웃\"");

		String loginId = "icon" + UUID.randomUUID().toString().substring(0, 6);
		memberRepository.save(new Member(loginId, passwordEncoder.encode("pw"), "아이콘", Role.USER));
		MockHttpSession user = login(loginId, "pw");
		String userHtml = mvc.perform(get("/").session(user)).andReturn().getResponse().getContentAsString();
		assertThat(userHtml).contains("aria-label=\"조직도\"", "aria-label=\"로그아웃\"")
			.doesNotContain("관리자 페이지");

		// 조직도는 팝업 전용 화면: 상단 메뉴 바 없이 [닫기]/[홈으로]만
		String orgHtml = mvc.perform(get("/org").session(user))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(orgHtml).contains("popup-body", "닫기", "홈으로").doesNotContain("class=\"menubar\"");
	}

	@Test
	void adminReordersMenus_userSeesNewOrder_thenReset() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin/menus").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("메뉴 순서")));

		mvc.perform(save(admin, "HOME", "APPROVAL", "BOARD", "ADDRESS", "SCHEDULE", "ROOM_RESERVATION", "RESERVATION", "ATTENDANCE"))
			.andExpect(flash().attribute("message", "메뉴 순서를 저장했습니다."));

		String loginId = "menu" + UUID.randomUUID().toString().substring(0, 6);
		memberRepository.save(new Member(loginId, passwordEncoder.encode("pw"), "메뉴확인", Role.USER));
		String html = mvc.perform(get("/schedule").session(login(loginId, "pw"))).andReturn().getResponse().getContentAsString();
		assertThat(menubar(html)).isEqualTo("홈 전자결재 게시판 주소록 일정 회의실예약 자원예약 근태관리");
		assertThat(html).containsPattern("href=\"/schedule\"\\s+class=\"active\"");

		mvc.perform(post("/admin/menus/reset").session(admin).with(csrf()))
			.andExpect(flash().attribute("message", containsString("기본값")));
		assertThat(menuService.ordered()).containsExactly(MainMenu.values());
	}

	@Test
	void rejectsIncompleteOrUnknownMenus_andNonAdmins() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(save(admin, "HOME", "BOARD"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		mvc.perform(save(admin, "HOME", "HOME", "BOARD", "APPROVAL", "SCHEDULE", "ADDRESS"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		mvc.perform(save(admin, "HOME", "NOPE", "BOARD", "APPROVAL", "SCHEDULE", "ADDRESS"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		mvc.perform(post("/admin/menus").session(admin).with(csrf()))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		assertThat(menuService.ordered()).isEqualTo(List.of(MainMenu.values()));

		String loginId = "nomenu" + UUID.randomUUID().toString().substring(0, 6);
		memberRepository.save(new Member(loginId, passwordEncoder.encode("pw"), "일반", Role.USER));
		MockHttpSession user = login(loginId, "pw");
		mvc.perform(get("/admin/menus").session(user)).andExpect(status().isForbidden());
		mvc.perform(save(user, "ADDRESS", "HOME", "BOARD", "APPROVAL", "SCHEDULE", "ROOM_RESERVATION", "RESERVATION", "ATTENDANCE"))
			.andExpect(status().isForbidden());
	}

	// ---------- 관리자 메뉴 순서 ----------

	/** 관리자 화면 상단 메뉴 바의 글자를 순서대로 */
	private static String adminbar(String html) {
		String bar = html.substring(html.indexOf(">", html.indexOf("admin-menubar")) + 1);
		return bar.substring(0, bar.indexOf("</nav>")).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
	}

	private static MockHttpServletRequestBuilder saveAdmin(MockHttpSession session, String... sections) {
		return post("/admin/menus/admin").session(session).with(csrf()).param("menus", sections);
	}

	@Test
	void adminReordersAdminMenu_everyManagerSeesNewOrder_thenReset() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		String page = mvc.perform(get("/admin/menus/admin").session(admin))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(page).contains("관리자 메뉴 순서", "사용자 메뉴 순서", "action=\"/admin/menus/admin\"");
		assertThat(adminbar(page)).isEqualTo("인사정보 권한 근태 게시판 전자결재 예약 화면");

		mvc.perform(saveAdmin(admin, "SCREEN", "BOARD", "HR", "PERMISSION", "ATTENDANCE", "APPROVAL", "RESERVATION"))
			.andExpect(redirectedUrl("/admin/menus/admin"))
			.andExpect(flash().attribute("message", "관리자 메뉴 순서를 저장했습니다."));
		String after = mvc.perform(get("/admin/members").session(admin)).andReturn().getResponse().getContentAsString();
		assertThat(adminbar(after)).isEqualTo("화면 게시판 인사정보 권한 근태 전자결재 예약");
		// 관리자 화면 첫 화면도 맨 앞 메뉴로 간다
		mvc.perform(get("/admin").session(admin)).andExpect(redirectedUrl("/admin/menus"));

		// 관리 권한만 있는 사람은 자기 메뉴만 같은 순서로
		String loginId = "mgr" + UUID.randomUUID().toString().substring(0, 6);
		Member manager = new Member(loginId, passwordEncoder.encode("pw"), "담당자", Role.USER);
		manager.grant(Permission.APPROVAL_MANAGER);
		manager.grant(Permission.BOARD_MANAGER);
		memberRepository.save(manager);
		MockHttpSession mgr = login(loginId, "pw");
		String mgrPage = mvc.perform(get("/admin/boards").session(mgr)).andReturn().getResponse().getContentAsString();
		assertThat(adminbar(mgrPage)).isEqualTo("게시판 전자결재");
		mvc.perform(get("/admin").session(mgr)).andExpect(redirectedUrl("/admin/boards"));
		mvc.perform(get("/admin/menus/admin").session(mgr)).andExpect(status().isForbidden());
		mvc.perform(saveAdmin(mgr, "HR", "PERMISSION", "ATTENDANCE", "BOARD", "APPROVAL", "RESERVATION", "SCREEN"))
			.andExpect(status().isForbidden());

		mvc.perform(post("/admin/menus/admin/reset").session(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/menus/admin"));
		assertThat(adminMenuService.ordered()).containsExactly(AdminSection.values());
		mvc.perform(get("/admin").session(admin)).andExpect(redirectedUrl("/admin/members"));
	}

	@Test
	void rejectsIncompleteAdminMenus() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(saveAdmin(admin, "HR", "BOARD"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		mvc.perform(saveAdmin(admin, "HR", "NOPE", "PERMISSION", "ATTENDANCE", "BOARD", "APPROVAL", "RESERVATION"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		// 사용자 메뉴 이름은 관리자 메뉴로 받지 않는다
		mvc.perform(saveAdmin(admin, "HOME", "BOARD", "APPROVAL", "SCHEDULE", "ROOM_RESERVATION", "RESERVATION", "ATTENDANCE"))
			.andExpect(flash().attribute("error", containsString("올바르지 않습니다")));
		assertThat(adminMenuService.ordered()).containsExactly(AdminSection.values());
	}
}
