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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.Role;
import th_prj.th_company.reservation.Reservation;
import th_prj.th_company.reservation.ReservationRepository;
import th_prj.th_company.reservation.Resource;
import th_prj.th_company.reservation.ResourceRepository;
import th_prj.th_company.reservation.ResourceType;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationTests {

	/** 지난 시간 검사에 걸리지 않게 먼 미래 날짜 */
	private static final LocalDate DAY = LocalDate.now().plusYears(2);

	@Autowired private MockMvc mvc;
	@Autowired private ResourceRepository resourceRepository;
	@Autowired private ReservationRepository reservationRepository;
	@Autowired private MemberRepository memberRepository;
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

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andReturn().getRequest().getSession();
	}

	private Resource resource(ResourceType type, String name) {
		return resourceRepository.save(new Resource(type, name + suffix, "본관 3층", 8, null, 0));
	}

	private static MockHttpServletRequestBuilder reserve(MockHttpSession s, String kind, Long resourceId, LocalDate date,
			String start, String end, String title) {
		return post("/reservations").session(s).with(csrf()).param("kind", kind)
			.param("resourceId", resourceId.toString()).param("date", date.toString())
			.param("startTime", start).param("endTime", end).param("title", title);
	}

	private List<Reservation> reservationsOf(Long resourceId) {
		return reservationRepository.findAll().stream()
			.filter(r -> r.getResource().getId().equals(resourceId))
			.toList();
	}

	@Test
	void roomReservation_overlapRejected_backToBackAllowed_onlyOwnerChanges() throws Exception {
		Resource room = resource(ResourceType.ROOM, "테스트룸");
		MockHttpSession a = login(user("resa"), "pw");
		MockHttpSession b = login(user("resb"), "pw");

		mvc.perform(get("/reservations/rooms/new").session(a).param("resource", room.getId().toString())
				.param("date", DAY.toString()).param("start", "10:00"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("회의실 예약하기")))
			.andExpect(content().string(containsString("value=\"10:00\"")))
			.andExpect(content().string(containsString("아직 예약이 없습니다")));

		mvc.perform(reserve(a, "ROOM", room.getId(), DAY, "10:00", "11:00", "주간 회의"))
			.andExpect(redirectedUrl("/reservations/rooms?date=" + DAY))
			.andExpect(flash().attribute("message", "예약했습니다."));

		// 회의실 현황에만 나온다
		mvc.perform(get("/reservations/rooms").session(b).param("date", DAY.toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("회의실 예약 현황")))
			.andExpect(content().string(containsString("테스트룸" + suffix)))
			.andExpect(content().string(containsString("10:00 ~ 11:00 · resa님")));
		mvc.perform(get("/reservations/resources").session(b).param("date", DAY.toString()))
			.andExpect(content().string(containsString("자원 예약 현황")))
			.andExpect(content().string(not(containsString("테스트룸" + suffix))));

		// 겹침은 거절, 끝나는 시각에 바로 이어서는 가능
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "10:30", "11:30", "겹치는 회의"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("시간이 겹칩니다")));
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "09:00", "12:00", "감싸는 회의"))
			.andExpect(content().string(containsString("시간이 겹칩니다")));
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "11:00", "12:00", "이어지는 회의"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "09:00", "10:00", "앞선 회의"))
			.andExpect(status().is3xxRedirection());
		assertThat(reservationsOf(room.getId())).hasSize(3);

		// 시간 검사: 10분 단위, 종료 > 시작, 지난 시간 불가
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "13:05", "14:00", "x"))
			.andExpect(content().string(containsString("10분 단위")));
		mvc.perform(reserve(b, "ROOM", room.getId(), DAY, "14:00", "14:00", "x"))
			.andExpect(content().string(containsString("종료 시간은 시작 시간보다 늦어야")));
		mvc.perform(reserve(b, "ROOM", room.getId(), LocalDate.now().minusDays(1), "10:00", "11:00", "x"))
			.andExpect(content().string(containsString("지난 시간에는 예약할 수 없습니다")));
		// 회의실을 자원 예약으로 넣을 수 없다
		mvc.perform(reserve(b, "RESOURCE", room.getId(), DAY, "15:00", "16:00", "x"))
			.andExpect(content().string(containsString("자원을(를) 찾을 수 없습니다")));

		// 남의 예약은 수정·취소 불가, 상세는 볼 수 있다
		Reservation mine = reservationsOf(room.getId()).stream()
			.filter(r -> r.getTitle().equals("주간 회의")).findFirst().orElseThrow();
		mvc.perform(get("/reservations/{id}", mine.getId()).session(b))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("회의실 예약 현황")))
			.andExpect(content().string(not(containsString("예약 취소"))));
		mvc.perform(get("/reservations/{id}/edit", mine.getId()).session(b)).andExpect(status().isForbidden());
		mvc.perform(post("/reservations/{id}/delete", mine.getId()).session(b).with(csrf()))
			.andExpect(status().isForbidden());

		// 본인 수정: 자기 시간과는 겹쳐도 되고, 다른 예약과는 안 된다
		mvc.perform(reserve(a, "ROOM", room.getId(), DAY, "10:00", "10:30", "주간 회의 (단축)").param("id", mine.getId().toString()))
			.andExpect(flash().attribute("message", "예약을 수정했습니다."));
		mvc.perform(reserve(a, "ROOM", room.getId(), DAY, "10:00", "11:10", "주간 회의").param("id", mine.getId().toString()))
			.andExpect(content().string(containsString("이어지는 회의")));

		// 내 회의실 예약 → 취소
		mvc.perform(get("/reservations/rooms/my").session(a))
			.andExpect(content().string(containsString("주간 회의 (단축)")));
		mvc.perform(get("/reservations/resources/my").session(a))
			.andExpect(content().string(not(containsString("주간 회의 (단축)"))));
		mvc.perform(post("/reservations/{id}/delete", mine.getId()).session(a).with(csrf()).param("back", "my"))
			.andExpect(redirectedUrl("/reservations/rooms/my"));
		assertThat(reservationRepository.findById(mine.getId())).isEmpty();
	}

	@Test
	void resourceManager_managesOnlyResources_roomManagerOnlyRooms() throws Exception {
		MockHttpSession plain = login(user("resp"), "pw");
		mvc.perform(get("/admin/resources").session(plain)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/rooms").session(plain)).andExpect(status().isForbidden());

		MockHttpSession rm = login(user("resm", Permission.RESOURCE_MANAGER), "pw");
		MockHttpSession roomM = login(user("roomm", Permission.ROOM_MANAGER), "pw");
		mvc.perform(get("/admin").session(rm)).andExpect(redirectedUrl("/admin/resources"));
		mvc.perform(get("/admin").session(roomM)).andExpect(redirectedUrl("/admin/rooms"));
		mvc.perform(get("/admin/rooms").session(rm)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/resources").session(roomM)).andExpect(status().isForbidden());
		mvc.perform(get("/admin/members").session(rm)).andExpect(status().isForbidden());

		// 자원 추가 (같은 이름 거절, 회의실 종류는 자원 관리에서 못 만든다)
		String name = "빔프로젝터" + suffix;
		mvc.perform(post("/admin/resources").session(rm).with(csrf())
				.param("type", "EQUIPMENT").param("name", name).param("location", "총무팀").param("sortOrder", "5"))
			.andExpect(redirectedUrl("/admin/resources"));
		mvc.perform(post("/admin/resources").session(rm).with(csrf())
				.param("type", "EQUIPMENT").param("name", name).param("sortOrder", "5"))
			.andExpect(content().string(containsString("같은 이름의 회의실이나 자원이 이미 있습니다")));
		mvc.perform(post("/admin/resources").session(rm).with(csrf())
				.param("type", "ROOM").param("name", "몰래회의실" + suffix).param("sortOrder", "5"))
			.andExpect(content().string(containsString("종류를 다시 선택하세요")));
		Resource beam = resourceRepository.findAll().stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow();
		mvc.perform(get("/admin/resources").session(rm))
			.andExpect(content().string(containsString(name)))
			.andExpect(content().string(containsString("자원 관리")))
			.andExpect(content().string(not(containsString("회의실 관리"))));

		// 회의실관리자는 회의실 관리 화면에서 이 자원을 다룰 수 없다
		Resource room = resource(ResourceType.ROOM, "관리룸");
		mvc.perform(get("/admin/rooms").session(roomM).param("edit", beam.getId().toString()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/admin/rooms").session(roomM))
			.andExpect(content().string(containsString("관리룸" + suffix)))
			.andExpect(content().string(not(containsString(name))));

		// 남의 예약: 자원관리자는 자원 예약만, 회의실관리자는 회의실 예약만 취소할 수 있다
		MockHttpSession owner = login(user("reso"), "pw");
		mvc.perform(reserve(owner, "RESOURCE", beam.getId(), DAY, "14:00", "16:00", "외부 발표")).andExpect(status().is3xxRedirection());
		mvc.perform(reserve(owner, "ROOM", room.getId(), DAY, "14:00", "16:00", "팀 회의")).andExpect(status().is3xxRedirection());
		Reservation beamRes = reservationsOf(beam.getId()).get(0);
		Reservation roomRes = reservationsOf(room.getId()).get(0);
		mvc.perform(get("/reservations/{id}", beamRes.getId()).session(rm))
			.andExpect(content().string(containsString("예약 취소")));
		mvc.perform(get("/reservations/{id}", roomRes.getId()).session(rm))
			.andExpect(content().string(not(containsString("예약 취소"))));
		mvc.perform(post("/reservations/{id}/delete", roomRes.getId()).session(rm).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/reservations/{id}/delete", beamRes.getId()).session(roomM).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/reservations/{id}/delete", roomRes.getId()).session(roomM).with(csrf()))
			.andExpect(redirectedUrl("/reservations/rooms?date=" + DAY));

		// 예약 기록이 있으면 삭제 불가 → 사용 안 함으로. 사용 안 함 자원은 새 예약을 받지 않는다
		mvc.perform(post("/admin/resources/{id}/delete", beam.getId()).session(rm).with(csrf()))
			.andExpect(flash().attribute("error", containsString("예약 기록이 1건 있어")));
		mvc.perform(post("/admin/resources").session(rm).with(csrf())
				.param("id", beam.getId().toString()).param("type", "EQUIPMENT").param("name", name).param("sortOrder", "5")
				.param("_enabled", "on"))
			.andExpect(redirectedUrl("/admin/resources"));
		assertThat(resourceRepository.findById(beam.getId()).orElseThrow().isEnabled()).isFalse();
		mvc.perform(reserve(owner, "RESOURCE", beam.getId(), DAY, "17:00", "18:00", "추가 발표"))
			.andExpect(content().string(containsString("지금 예약을 받지 않습니다")));
		mvc.perform(get("/reservations/resources/new").session(owner))
			.andExpect(content().string(not(containsString(name))));

		mvc.perform(post("/reservations/{id}/delete", beamRes.getId()).session(rm).with(csrf()))
			.andExpect(redirectedUrl("/reservations/resources?date=" + DAY));
		mvc.perform(post("/admin/resources/{id}/delete", beam.getId()).session(rm).with(csrf()))
			.andExpect(flash().attribute("message", "삭제했습니다."));
		assertThat(resourceRepository.findById(beam.getId())).isEmpty();
	}

	@Test
	void menusAndEmptyDayBoard() throws Exception {
		MockHttpSession s = login("sysadmin", "a");
		mvc.perform(get("/reservations").session(s)).andExpect(redirectedUrl("/reservations/rooms"));
		mvc.perform(get("/reservations/rooms").session(s).param("date", "2040-01-07"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("2040년 1월 7일 (토)")))
			.andExpect(content().string(containsString("이 날 예약 0건")))
			.andExpect(content().string(not(containsString("res-tabs"))));
		mvc.perform(get("/reservations/resources").session(s).param("date", "2040-01-07"))
			.andExpect(content().string(containsString("res-tabs")))
			.andExpect(content().string(containsString("차량")));
		mvc.perform(get("/reservations/cars").session(s)).andExpect(status().isNotFound());
		mvc.perform(get("/reservations/rooms").session(s).param("date", "아무거나"))
			.andExpect(status().isBadRequest());
	}
}
