package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;

@SpringBootTest
@AutoConfigureMockMvc
class MyPageTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String loginId;

	@BeforeEach
	void setUp() {
		loginId = "my" + UUID.randomUUID().toString().substring(0, 6);
		Member m = new Member(loginId, passwordEncoder.encode("pw"), "마이", Role.USER);
		m.updateProfile("마이", "E" + loginId, loginId + "@test.local", "010-1111-2222", null, null, null, null);
		m.grant(Permission.ROOM_MANAGER);
		memberRepository.save(m);
	}

	private MockHttpSession login(String id, String pw) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(id).password(pw))
			.andReturn().getRequest().getSession();
	}

	private Member me() {
		return memberRepository.findByLoginId(loginId).orElseThrow();
	}

	private static MockHttpServletRequestBuilder password(MockHttpSession s, String current, String next, String confirm) {
		return post("/my/password").session(s).with(csrf())
			.param("currentPassword", current).param("newPassword", next).param("confirmPassword", confirm);
	}

	@Test
	void 상단_아이콘으로_마이페이지를_열면_프로필과_상태가_보인다() throws Exception {
		MockHttpSession s = login(loginId, "pw");
		mvc.perform(get("/").session(s))
			.andExpect(content().string(containsString("aria-label=\"마이페이지\"")));
		mvc.perform(get("/my").session(s))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(loginId + "@test.local")))
			.andExpect(content().string(containsString("010-1111-2222")))
			.andExpect(content().string(containsString("회의실관리자")))
			.andExpect(content().string(containsString("결재할 문서")))
			.andExpect(content().string(containsString("다가오는 예약이 없습니다")));
	}

	@Test
	void 연락처_주소_사진은_고치지만_인사정보는_못_고친다() throws Exception {
		MockHttpSession s = login(loginId, "pw");
		mvc.perform(get("/my/edit").session(s))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("주소 검색")));

		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png);
		mvc.perform(multipart("/my/edit").file(new MockMultipartFile("photo", "me.png", "image/png", png.toByteArray()))
				.session(s).with(csrf())
				.param("email", "  new-" + loginId + "@test.local ").param("phone", "010-3333-4444")
				.param("zipCode", "13494").param("roadAddress", "경기 성남시 분당구 판교역로 235")
				.param("jibunAddress", "경기 성남시 분당구 삼평동 681").param("addressDetail", "3층")
				.param("name", "바꾼이름"))
			.andExpect(redirectedUrl("/my"))
			.andExpect(flash().attribute("message", "내 정보를 저장했습니다."));
		Member m = me();
		assertThat(m.getEmail()).isEqualTo("new-" + loginId + "@test.local");
		assertThat(m.getPhone()).isEqualTo("010-3333-4444");
		assertThat(m.getAddress().getDetail()).isEqualTo("3층");
		assertThat(m.getPhotoPath()).isNotNull();
		assertThat(m.getName()).isEqualTo("마이");

		// 잘못된 이메일은 거절 (남이 쓰는 이메일은 duplicateEmailRejected)
		mvc.perform(multipart("/my/edit").session(s).with(csrf()).param("email", "not-an-email"))
			.andExpect(content().string(containsString("이메일 형식이 올바르지 않습니다")));

		// 사진 삭제
		mvc.perform(multipart("/my/edit").session(s).with(csrf())
				.param("email", m.getEmail()).param("removePhoto", "true"))
			.andExpect(redirectedUrl("/my"));
		assertThat(me().getPhotoPath()).isNull();
	}

	@Test
	void 중복_이메일은_거부된다() throws Exception {
		String otherId = "myo" + UUID.randomUUID().toString().substring(0, 6);
		Member other = new Member(otherId, passwordEncoder.encode("pw"), "남", Role.USER);
		other.updateProfile("남", null, otherId + "@test.local", null, null, null, null, null);
		memberRepository.save(other);

		MockHttpSession s = login(loginId, "pw");
		mvc.perform(multipart("/my/edit").session(s).with(csrf()).param("email", otherId.toUpperCase() + "@TEST.LOCAL"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이미 다른 사람이 쓰고 있는 이메일입니다")));
		assertThat(me().getEmail()).isEqualTo(loginId + "@test.local");
	}

	@Test
	void 비밀번호_변경은_검사를_거치고_새_비밀번호로_로그인된다() throws Exception {
		MockHttpSession s = login(loginId, "pw");
		mvc.perform(get("/my/password").session(s)).andExpect(status().isOk());

		mvc.perform(password(s, "wrong", "newpass123", "newpass123"))
			.andExpect(content().string(containsString("현재 비밀번호가 맞지 않습니다")));
		mvc.perform(password(s, "pw", "short1", "short1"))
			.andExpect(content().string(containsString("영문과 숫자를 섞어 8자 이상")));
		mvc.perform(password(s, "pw", "onlyletters", "onlyletters"))
			.andExpect(content().string(containsString("영문과 숫자를 섞어 8자 이상")));
		mvc.perform(password(s, "pw", "newpass123", "newpass124"))
			.andExpect(content().string(containsString("새 비밀번호 확인이 일치하지 않습니다")));

		mvc.perform(password(s, "pw", "newpass123", "newpass123"))
			.andExpect(redirectedUrl("/my"));
		mvc.perform(password(s, "newpass123", "newpass123", "newpass123"))
			.andExpect(content().string(containsString("지금 쓰는 비밀번호와 다른")));

		mvc.perform(formLogin("/login").user(loginId).password("pw")).andExpect(unauthenticated());
		mvc.perform(formLogin("/login").user(loginId).password("newpass123")).andExpect(authenticated());
	}

	@Test
	void 로그인이_필요하다() throws Exception {
		mvc.perform(get("/my")).andExpect(status().is3xxRedirection());
	}
}
