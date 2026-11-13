package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.address.AddressService;
import th_prj.th_company.address.AddressViews.ContactRow;
import th_prj.th_company.address.AddressViews.Employee;
import th_prj.th_company.address.ContactGroupRepository;
import th_prj.th_company.address.ContactRepository;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AddressBookTests {

	@Autowired private MockMvc mvc;
	@Autowired private AddressService addressService;
	@Autowired private ContactRepository contactRepository;
	@Autowired private ContactGroupRepository groupRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String suffix;
	private Department division;
	private Department team;
	private Member me;
	private Member colleague;
	private MockHttpSession session;

	@BeforeEach
	void setUp() throws Exception {
		suffix = UUID.randomUUID().toString().substring(0, 6);
		division = departmentRepository.save(new Department("주소본부" + suffix, null, 900));
		team = departmentRepository.save(new Department("주소팀" + suffix, division, 10));
		me = member("me" + suffix, "주소록나" + suffix, division, "me" + suffix + "@th.com");
		colleague = member("col" + suffix, "주소록동료" + suffix, team, "col" + suffix + "@th.com");
		Member retired = member("ret" + suffix, "주소록퇴사" + suffix, team, null);
		retired.changeAccess(Role.USER, false);
		memberRepository.save(retired);
		session = (MockHttpSession) mvc.perform(formLogin("/login").user(me.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
	}

	private Member member(String loginId, String name, Department dept, String email) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), name, Role.USER);
		m.updateProfile(name, null, email, "010-1234-5678", dept, null, null, null);
		return memberRepository.save(m);
	}

	@Test
	void 사내주소록은_재직자만_검색하고_부서를_고르면_하위부서까지_나온다() throws Exception {
		assertThat(addressService.employees(suffix, null, 0).getContent()).extracting(Employee::name)
			.containsExactlyInAnyOrder(me.getName(), colleague.getName());
		// 본부를 고르면 하위 팀 인원까지, 팀을 고르면 팀 인원만
		assertThat(addressService.employees(null, division.getId(), 0).getContent()).extracting(Employee::name)
			.containsExactlyInAnyOrder(me.getName(), colleague.getName());
		assertThat(addressService.employees(null, team.getId(), 0).getContent()).extracting(Employee::name)
			.containsExactly(colleague.getName());
		// 부서 이름, 이메일로도 찾음
		assertThat(addressService.employees("주소팀" + suffix, null, 0).getContent()).extracting(Employee::name)
			.containsExactly(colleague.getName());
		assertThat(addressService.employees("col" + suffix + "@", null, 0).getTotalElements()).isEqualTo(1);

		mvc.perform(get("/address").session(session)).andExpect(redirectedUrl("/address/company"));
		mvc.perform(get("/address/company").session(session).param("q", suffix))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(colleague.getName())))
			.andExpect(content().string(not(containsString("주소록퇴사" + suffix))))
			.andExpect(content().string(containsString("주소록")));
	}

	@Test
	void 개인주소록_등록_수정_삭제_즐겨찾기_그룹_검색() throws Exception {
		mvc.perform(post("/address/groups").session(session).with(csrf()).param("name", "거래처"))
			.andExpect(flash().attribute("message", containsString("거래처")));
		mvc.perform(post("/address/groups").session(session).with(csrf()).param("name", " 거래처 "))
			.andExpect(flash().attribute("error", containsString("이미 있습니다")));
		Long groupId = groupRepository.findByOwnerIdOrderByNameAsc(me.getId()).get(0).getId();

		// 이메일 형식 오류는 폼으로 돌아감
		mvc.perform(post("/address/personal").session(session).with(csrf())
				.param("name", "김거래").param("email", "not-mail"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이메일 형식이 아닙니다")));

		mvc.perform(post("/address/personal").session(session).with(csrf())
				.param("name", "김거래").param("company", "(주)가나상사").param("position", "과장")
				.param("mobile", "010-9999-0000").param("email", "kim@gana.com").param("groupId", groupId.toString()))
			.andExpect(redirectedUrl("/address/personal?g=" + groupId));
		mvc.perform(post("/address/personal").session(session).with(csrf()).param("name", "박협력"))
			.andExpect(redirectedUrl("/address/personal"));

		assertThat(addressService.contacts(me.getLoginId(), false, groupId, null, 0).getContent())
			.extracting(ContactRow::name).containsExactly("김거래");
		assertThat(addressService.contacts(me.getLoginId(), false, null, "가나", 0).getContent())
			.extracting(ContactRow::affiliation).containsExactly("(주)가나상사 · 과장");
		Long kimId = addressService.contacts(me.getLoginId(), false, groupId, null, 0).getContent().get(0).id();

		mvc.perform(post("/address/personal/{id}/favorite", kimId).session(session).with(csrf())
				.param("back", "/address/personal?fav=true"))
			.andExpect(redirectedUrl("/address/personal?fav=true"));
		assertThat(addressService.contacts(me.getLoginId(), true, null, null, 0).getContent())
			.extracting(ContactRow::name).containsExactly("김거래");
		// 주소록 밖으로는 돌려보내지 않음
		mvc.perform(post("/address/personal/{id}/favorite", kimId).session(session).with(csrf())
				.param("back", "https://evil.example.com"))
			.andExpect(redirectedUrl("/address/personal"));

		mvc.perform(get("/address/personal").session(session).param("g", groupId.toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h2>거래처</h2>")))
			.andExpect(content().string(containsString("김거래")))
			.andExpect(content().string(containsString("총 1건")));

		// 그룹을 지우면 연락처는 남고 그룹 없음
		mvc.perform(post("/address/groups/{id}/delete", groupId).session(session).with(csrf()))
			.andExpect(flash().attribute("message", containsString("삭제했습니다")));
		assertThat(contactRepository.findById(kimId).orElseThrow().getGroup()).isNull();

		mvc.perform(post("/address/personal/{id}/delete", kimId).session(session).with(csrf()))
			.andExpect(flash().attribute("message", "김거래 연락처를 삭제했습니다."));
		assertThat(contactRepository.findById(kimId)).isEmpty();
	}

	@Test
	void 주소를_연락처와_직원정보에_저장하고_주소검색_버튼을_보여준다() throws Exception {
		// 개인 주소록: 검색으로 채운 주소 + 상세주소, 다시 열면 그대로
		mvc.perform(get("/address/personal/new").session(session))
			.andExpect(content().string(containsString("data-addr-search")));
		mvc.perform(post("/address/personal").session(session).with(csrf()).param("name", "주소있는사람")
				.param("zipCode", "06236").param("roadAddress", "서울 강남구 테헤란로 152 (역삼동)")
				.param("jibunAddress", "서울 강남구 역삼동 737").param("addressDetail", " 12층 "))
			.andExpect(status().is3xxRedirection());
		Long id = addressService.contacts(me.getLoginId(), false, null, "주소있는", 0).getContent().get(0).id();
		mvc.perform(get("/address/personal/{id}/edit", id).session(session))
			.andExpect(content().string(containsString("value=\"06236\"")))
			.andExpect(content().string(containsString("value=\"서울 강남구 역삼동 737\"")))
			.andExpect(content().string(containsString("value=\"12층\"")));

		// 우편번호가 5자리가 아니면 다시 입력
		mvc.perform(post("/address/personal").session(session).with(csrf()).param("name", "잘못된주소").param("zipCode", "12"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("우편번호는 숫자 5자리")));

		// 관리자 사용자 수정: 주소를 넣고, 지우기 하면 모두 비워짐
		MockHttpSession admin = (MockHttpSession) mvc.perform(formLogin("/login").user("sysadmin").password("a"))
			.andReturn().getRequest().getSession();
		mvc.perform(get("/admin/members/{id}", colleague.getId()).session(admin))
			.andExpect(content().string(containsString("data-addr-search")));
		mvc.perform(post("/admin/members/{id}", colleague.getId()).session(admin).with(csrf())
				.param("loginId", colleague.getLoginId()).param("name", colleague.getName())
				.param("role", "USER").param("enabled", "true")
				.param("zipCode", "13529").param("roadAddress", "경기 성남시 분당구 판교역로 235 (삼평동)")
				.param("jibunAddress", "경기 성남시 분당구 삼평동 681").param("addressDetail", "101동 1001호"))
			.andExpect(status().is3xxRedirection());
		var saved = memberRepository.findById(colleague.getId()).orElseThrow().getAddress();
		assertThat(saved.getZipCode()).isEqualTo("13529");
		assertThat(saved.getRoadAddress()).isEqualTo("경기 성남시 분당구 판교역로 235 (삼평동)");
		assertThat(saved.getDetail()).isEqualTo("101동 1001호");

		mvc.perform(post("/admin/members/{id}", colleague.getId()).session(admin).with(csrf())
				.param("loginId", colleague.getLoginId()).param("name", colleague.getName())
				.param("role", "USER").param("enabled", "true")
				.param("zipCode", "").param("roadAddress", "").param("jibunAddress", "").param("addressDetail", ""))
			.andExpect(status().is3xxRedirection());
		assertThat(memberRepository.findById(colleague.getId()).orElseThrow().getAddress()).isNull();
	}

	@Test
	void 다른_사람은_내_연락처를_보거나_바꿀_수_없다() throws Exception {
		mvc.perform(post("/address/personal").session(session).with(csrf()).param("name", "비밀연락처" + suffix));
		Long id = addressService.contacts(me.getLoginId(), false, null, null, 0).getContent().get(0).id();

		MockHttpSession other = (MockHttpSession) mvc.perform(formLogin("/login").user(colleague.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
		mvc.perform(get("/address/personal").session(other))
			.andExpect(content().string(not(containsString("비밀연락처" + suffix))));
		mvc.perform(get("/address/personal/{id}/edit", id).session(other)).andExpect(status().isForbidden());
		mvc.perform(post("/address/personal/{id}/delete", id).session(other).with(csrf())).andExpect(status().isForbidden());
		assertThat(contactRepository.findById(id)).isPresent();
	}

	@Test
	void 사내주소록에서_직원을_개인주소록에_추가하고_CSV로_내보낸다() throws Exception {
		mvc.perform(post("/address/personal/add-employee").session(session).with(csrf())
				.param("memberId", colleague.getId().toString()).param("back", "/address/company?q=" + suffix))
			.andExpect(redirectedUrl("/address/company?q=" + suffix))
			.andExpect(flash().attribute("message", colleague.getName() + "님을 개인 주소록에 추가했습니다."));
		mvc.perform(post("/address/personal/add-employee").session(session).with(csrf())
				.param("memberId", colleague.getId().toString()))
			.andExpect(flash().attribute("error", containsString("이미 개인 주소록에 있습니다")));
		mvc.perform(post("/address/personal/add-employee").session(session).with(csrf())
				.param("memberId", me.getId().toString()))
			.andExpect(flash().attribute("error", containsString("본인")));

		ContactRow row = addressService.contacts(me.getLoginId(), false, null, null, 0).getContent().get(0);
		assertThat(row.department()).isEqualTo(team.getName());
		assertThat(row.mobile()).isEqualTo("010-1234-5678");

		mvc.perform(post("/address/personal").session(session).with(csrf())
				.param("name", "=수식, \"따옴표\"").param("memo", "줄\n바꿈")
				.param("zipCode", "13529").param("roadAddress", "경기 성남시 분당구 판교역로 235 (삼평동)")
				.param("jibunAddress", "경기 성남시 분당구 삼평동 681").param("addressDetail", "3층"));
		byte[] csv = mvc.perform(get("/address/personal/export").session(session))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andReturn().getResponse().getContentAsByteArray();
		String text = new String(csv, StandardCharsets.UTF_8);
		assertThat(text).startsWith("﻿이름,회사,부서,직위,휴대폰,전화,이메일,우편번호,도로명주소,지번주소,상세주소,그룹");
		assertThat(text).contains(",13529,경기 성남시 분당구 판교역로 235 (삼평동),경기 성남시 분당구 삼평동 681,3층,");
		assertThat(text).contains(colleague.getName() + ",TH Company," + team.getName());
		assertThat(text).contains("\"'=수식, \"\"따옴표\"\"\"");
		assertThat(text).contains("\"줄\n바꿈\"");
	}
}
