package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;
import th_prj.th_company.org.HrCode;
import th_prj.th_company.org.HrCodeRepository;
import th_prj.th_company.org.HrCodeType;

@SpringBootTest
@AutoConfigureMockMvc
class ProfilePhotoTests {

	@Autowired private MockMvc mvc;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private HrCodeRepository hrCodeRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andReturn().getRequest().getSession();
	}

	private static byte[] png(int width, int height) throws Exception {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.ORANGE);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	/** 관리자 사용자 수정 화면 저장 (사진 포함) */
	private MockMultipartHttpServletRequestBuilder saveMember(MockHttpSession admin, Member m) {
		var request = multipart("/admin/members/{id}", m.getId());
		request.param("loginId", m.getLoginId()).param("name", m.getName())
			.param("departmentId", m.getDepartment().getId().toString())
			.param("role", "USER").param("enabled", "true")
			.session(admin).with(csrf());
		return request;
	}

	@Test
	void adminUploadsPhoto_orgChartShowsTopPersonDetailWithPhoto_thenRemoves() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		Department team = departmentRepository.save(new Department("사진팀" + suffix, null, 990));
		HrCode leaderDuty = hrCodeRepository.save(new HrCode(HrCodeType.DUTY, "사진팀장" + suffix, 1));

		Member staff = new Member("photo_staff" + suffix, passwordEncoder.encode("pw"), "나사원", Role.USER);
		staff.updateProfile("나사원", null, null, null, team, null, null, null);
		staff = memberRepository.save(staff);
		Member leader = new Member("photo_lead" + suffix, passwordEncoder.encode("pw"), "가팀장", Role.USER);
		leader.updateProfile("가팀장", "P" + suffix, "lead" + suffix + "@th.com", "010-1234-5678", team,
			null, leaderDuty, LocalDate.of(2015, 3, 2));
		leader = memberRepository.save(leader);

		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin/members/{id}", leader.getId()).session(admin))
			.andExpect(content().string(containsString("multipart/form-data")))
			.andExpect(content().string(containsString("name=\"photo\"")));

		// 이미지가 아니면 거절하고 사용자 정보도 저장하지 않는다
		mvc.perform(saveMember(admin, leader)
				.file(new MockMultipartFile("photo", "evil.png", "image/png", "<script>".getBytes(StandardCharsets.UTF_8)))
				.param("phone", "010-0000-0000"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("JPG, PNG, GIF, BMP 이미지만")));
		Member unchanged = memberRepository.findById(leader.getId()).orElseThrow();
		assertThat(unchanged.getPhotoPath()).isNull();

		// 가로로 긴 PNG → 정사각형 JPEG 로 저장
		mvc.perform(saveMember(admin, leader)
				.file(new MockMultipartFile("photo", "me.png", "image/png", png(800, 400)))
				.param("employeeNo", "P" + suffix).param("email", "lead" + suffix + "@th.com")
				.param("phone", "010-1234-5678").param("hireDate", "2015-03-02")
				.param("dutyId", leaderDuty.getId().toString()))
			.andExpect(status().is3xxRedirection());
		Member withPhoto = memberRepository.findById(leader.getId()).orElseThrow();
		assertThat(withPhoto.getPhotoUrl()).startsWith("/members/" + leader.getId() + "/photo?v=");

		// 다른 사용자도 사진을 볼 수 있다
		MockHttpSession user = login(staff.getLoginId(), "pw");
		byte[] served = mvc.perform(get("/members/{id}/photo", leader.getId()).session(user))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"))
			.andReturn().getResponse().getContentAsByteArray();
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(served));
		assertThat(img.getWidth()).isEqualTo(img.getHeight()).isLessThanOrEqualTo(320);
		mvc.perform(get("/members/{id}/photo", staff.getId()).session(user)).andExpect(status().isNotFound());

		// 조직도: 이 팀 소속이 아닌 사람이 보면 맨 위 사람(직책이 있는 팀장)의 상세정보 카드가 먼저 보이고 사진이 나온다
		String html = mvc.perform(get("/org").param("dept", team.getId().toString()).session(admin))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int firstCard = html.indexOf("data-card=\"" + leader.getId() + "\"");
		assertThat(firstCard).isPositive();
		assertThat(html.substring(firstCard, html.indexOf(">", firstCard))).doesNotContain("hidden");
		String otherCard = html.substring(html.indexOf("data-card=\"" + staff.getId() + "\""));
		assertThat(otherCard.substring(0, otherCard.indexOf(">"))).contains("hidden");
		assertThat(html).contains(withPhoto.getPhotoUrl().replace("&", "&amp;"), "2015.03.02", "tel:010-1234-5678");

		// 사진 삭제
		mvc.perform(saveMember(admin, withPhoto).param("removePhoto", "true"))
			.andExpect(status().is3xxRedirection());
		assertThat(memberRepository.findById(leader.getId()).orElseThrow().getPhotoPath()).isNull();
		mvc.perform(get("/members/{id}/photo", leader.getId()).session(user)).andExpect(status().isNotFound());
	}

	@Test
	void disabledMembersPhotoHiddenFromUsers() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 6);
		Department team = departmentRepository.save(new Department("퇴사팀" + suffix, null, 991));
		Member left = new Member("photo_left" + suffix, passwordEncoder.encode("pw"), "퇴사자", Role.USER);
		left.updateProfile("퇴사자", null, null, null, team, null, null, null);
		left = memberRepository.save(left);

		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(saveMember(admin, left).file(new MockMultipartFile("photo", "a.png", "image/png", png(50, 50))))
			.andExpect(status().is3xxRedirection());
		Member m = memberRepository.findById(left.getId()).orElseThrow();
		m.changeAccess(Role.USER, false);
		memberRepository.save(m);

		String viewerId = "photo_view" + suffix;
		memberRepository.save(new Member(viewerId, passwordEncoder.encode("pw"), "보는사람", Role.USER));
		mvc.perform(get("/members/{id}/photo", left.getId()).session(login(viewerId, "pw")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/members/{id}/photo", left.getId()).session(admin))
			.andExpect(status().isOk());
	}
}
