package th_prj.th_company.dev;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.reservation.Reservation;
import th_prj.th_company.reservation.ReservationRepository;
import th_prj.th_company.reservation.Resource;
import th_prj.th_company.reservation.ResourceRepository;
import th_prj.th_company.reservation.ResourceType;

/**
 * 개발용 자원예약 샘플 데이터. 평소에는 동작하지 않고, 아래처럼 실행할 때만 돈다.
 * <pre>
 * gradlew bootRun --args="--app.sample-data=reservations --app.sample-data.exit=true --server.port=0"
 * </pre>
 * 회의실 5개와 비품·장비·차량 10개를 (같은 이름이 없으면) 만들고, 예약이 하나도 없을 때만
 * 지난 2주 ~ 앞으로 2주의 평일에 부서가 있는 사용자 이름으로 겹치지 않는 예약을 채운다.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.sample-data", havingValue = "reservations")
public class SampleReservationSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SampleReservationSeeder.class);

	private record Spec(ResourceType type, String name, String location, Integer capacity, String description) {
	}

	private static final List<Spec> RESOURCES = List.of(
		new Spec(ResourceType.ROOM, "대회의실", "본관 5층", 20, "빔프로젝터, 화상회의 장비, 마이크 2대"),
		new Spec(ResourceType.ROOM, "중회의실 A", "본관 4층", 10, "75인치 TV, 화이트보드"),
		new Spec(ResourceType.ROOM, "중회의실 B", "본관 4층", 10, "75인치 TV, 화이트보드"),
		new Spec(ResourceType.ROOM, "소회의실 1", "본관 3층", 4, "1:1 면담, 소규모 회의용"),
		new Spec(ResourceType.ROOM, "소회의실 2", "본관 3층", 6, "화상회의 카메라"),
		new Spec(ResourceType.EQUIPMENT, "빔프로젝터 1", "총무팀", null, "HDMI·USB-C 젠더 포함"),
		new Spec(ResourceType.EQUIPMENT, "빔프로젝터 2", "총무팀", null, "휴대용 스크린 포함"),
		new Spec(ResourceType.EQUIPMENT, "노트북 (대여용) 1", "IT지원실", null, "15인치, 윈도우 11, 오피스 설치"),
		new Spec(ResourceType.EQUIPMENT, "노트북 (대여용) 2", "IT지원실", null, "14인치, 윈도우 11, 오피스 설치"),
		new Spec(ResourceType.EQUIPMENT, "DSLR 카메라", "총무팀", null, "행사 촬영용, 배터리 2개"),
		new Spec(ResourceType.EQUIPMENT, "무선 마이크 세트", "총무팀", null, "핸드 마이크 2개 + 포터블 스피커"),
		new Spec(ResourceType.EQUIPMENT, "화상회의 카메라", "IT지원실", null, "이동식, USB 연결"),
		new Spec(ResourceType.VEHICLE, "업무차량 1 (12가 3456)", "지하 2층 주차장", 5, "중형 세단, 하이패스 장착"),
		new Spec(ResourceType.VEHICLE, "업무차량 2 (34나 7890)", "지하 2층 주차장", 5, "준중형 SUV"),
		new Spec(ResourceType.VEHICLE, "승합차 (56다 1234)", "지하 2층 주차장", 11, "11인승, 외부 행사·출장용")
	);

	private static final String[] ROOM_TITLES = {
		"주간 팀 회의", "프로젝트 킥오프", "고객사 미팅", "신규 입사자 교육", "분기 실적 리뷰", "기획안 검토",
		"면접", "1:1 면담", "디자인 리뷰", "코드 리뷰", "협력사 업무 협의", "부서장 회의", "아이디어 회의", "보고서 검토"
	};
	private static final String[] EQUIP_TITLES = {
		"외부 발표", "고객사 시연", "사내 교육 발표", "출장 업무용", "행사 촬영", "세미나 진행", "화상회의"
	};
	private static final String[] VEHICLE_TITLES = {
		"고객사 방문", "협력사 미팅 이동", "지방 출장", "행사 물품 운반", "공장 방문", "관공서 서류 제출"
	};

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;
	private final TransactionTemplate tx;
	private final ApplicationContext context;
	private final Random random = new Random(20261028L);

	public SampleReservationSeeder(ResourceRepository resourceRepository, ReservationRepository reservationRepository,
			MemberRepository memberRepository, TransactionTemplate tx, ApplicationContext context) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
		this.memberRepository = memberRepository;
		this.tx = tx;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		tx.executeWithoutResult(s -> seedResources());

		List<Member> staff = memberRepository.findActiveWithDepartment();
		if (staff.isEmpty()) {
			log.warn("부서가 지정된 사용자가 없어 샘플 예약을 만들지 않습니다.");
		} else if (reservationRepository.count() > 0) {
			log.info("이미 예약이 {}건 있어 샘플 예약은 건너뜁니다.", reservationRepository.count());
		} else {
			int count = tx.execute(s -> seedReservations(staff));
			log.info("샘플 예약 {}건을 추가했습니다.", count);
		}

		if (args.containsOption("app.sample-data.exit")) {
			// 데이터만 넣고 바로 종료
			System.exit(SpringApplication.exit(context, () -> 0));
		}
	}

	private void seedResources() {
		Set<String> existing = resourceRepository.findAll().stream().map(Resource::getName).collect(Collectors.toSet());
		int added = 0;
		for (int i = 0; i < RESOURCES.size(); i++) {
			Spec s = RESOURCES.get(i);
			if (existing.contains(s.name())) {
				continue;
			}
			resourceRepository.save(new Resource(s.type(), s.name(), s.location(), s.capacity(), s.description(), (i + 1) * 10));
			added++;
		}
		log.info("샘플 자원 {}개를 추가했습니다 (이미 있던 이름은 건너뜀).", added);
	}

	private int seedReservations(List<Member> staff) {
		LocalDate today = LocalDate.now();
		List<Resource> resources = resourceRepository.findEnabled();
		int count = 0;
		for (LocalDate d = today.minusDays(14); !d.isAfter(today.plusDays(14)); d = d.plusDays(1)) {
			if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			for (Resource r : resources) {
				count += fillDay(r, d, staff);
			}
		}
		return count;
	}

	/** 한 자원의 하루: 09:00 부터 빈 시간을 두며 차례로 예약을 넣어 겹치지 않게 한다 */
	private int fillDay(Resource r, LocalDate day, List<Member> staff) {
		int wanted;
		int[] lengths; // 분
		String[] titles;
		switch (r.getType()) {
			case ROOM -> {
				wanted = 2 + random.nextInt(4);
				lengths = new int[] {30, 60, 60, 60, 90, 120};
				titles = ROOM_TITLES;
			}
			case EQUIPMENT -> {
				wanted = random.nextInt(3);
				lengths = new int[] {60, 120, 180, 240};
				titles = EQUIP_TITLES;
			}
			default -> {
				wanted = random.nextInt(2);
				lengths = new int[] {120, 180, 240, 480};
				titles = VEHICLE_TITLES;
			}
		}
		LocalDateTime cursor = day.atTime(9, 0);
		LocalDateTime limit = day.atTime(18, 0);
		List<Reservation> made = new ArrayList<>();
		for (int i = 0; i < wanted; i++) {
			cursor = cursor.plusMinutes(30L * random.nextInt(4)); // 0 ~ 90분 빈 시간
			if (cursor.toLocalTime().equals(LocalTime.of(12, 0)) || cursor.toLocalTime().equals(LocalTime.of(12, 30))) {
				cursor = day.atTime(13, 0); // 점심시간은 비운다
			}
			LocalDateTime end = cursor.plusMinutes(lengths[random.nextInt(lengths.length)]);
			if (end.isAfter(limit)) {
				break;
			}
			Member owner = staff.get(random.nextInt(staff.size()));
			Reservation res = new Reservation(owner);
			res.update(r, titles[random.nextInt(titles.length)], cursor, end, null);
			made.add(res);
			cursor = end;
		}
		reservationRepository.saveAll(made);
		return made.size();
	}
}
