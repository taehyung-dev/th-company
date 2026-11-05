package th_prj.th_company.dev;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import th_prj.th_company.board.Board;
import th_prj.th_company.board.BoardRepository;
import th_prj.th_company.board.Comment;
import th_prj.th_company.board.CommentRepository;
import th_prj.th_company.board.Post;
import th_prj.th_company.board.PostRepository;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;

/**
 * 개발용 게시판 샘플 데이터. 평소에는 동작하지 않고, 아래처럼 실행할 때만 돈다.
 * <pre>
 * gradlew bootRun --args="--app.sample-data=posts --app.sample-data.exit=true --server.port=0"
 * </pre>
 * 게시판마다 글이 50개가 될 때까지 채운다 (이미 50개 이상이면 건너뜀). 작성자는 부서가 있는 사용자 중에서,
 * 공지(관리자만 글쓰기) 게시판은 sysadmin 으로 쓴다. 작성일은 최근 6개월에 고르게 흩어 놓는다.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.sample-data", havingValue = "posts")
public class SamplePostSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SamplePostSeeder.class);
	private static final int TARGET_PER_BOARD = 50;

	private final BoardRepository boardRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final MemberRepository memberRepository;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final ApplicationContext context;
	private final Random random = new Random(20261024L);
	private List<Member> staff = List.of();

	public SamplePostSeeder(BoardRepository boardRepository, PostRepository postRepository,
			CommentRepository commentRepository, MemberRepository memberRepository,
			JdbcTemplate jdbc, TransactionTemplate tx, ApplicationContext context) {
		this.boardRepository = boardRepository;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.memberRepository = memberRepository;
		this.jdbc = jdbc;
		this.tx = tx;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		staff = memberRepository.findActiveWithDepartment();
		Member admin = memberRepository.findByLoginId("sysadmin").orElseThrow();
		if (staff.isEmpty()) {
			log.warn("부서가 지정된 사용자가 없어 샘플 게시글을 만들지 않습니다.");
			return;
		}

		for (Board board : boardRepository.findAllByOrderBySortOrderAscNameAsc()) {
			if (board.isFolder() || !board.isEnabled()) {
				continue;
			}
			long existing = postRepository.countByBoardId(board.getId());
			int toAdd = (int) Math.max(0, TARGET_PER_BOARD - existing);
			if (toAdd == 0) {
				log.info("[{}] 이미 글이 {}개 있어 건너뜁니다.", board.getName(), existing);
				continue;
			}
			Generator gen = generatorFor(board.getName());
			tx.executeWithoutResult(s -> seed(board, toAdd, gen, staff, admin));
			log.info("[{}] 샘플 게시글 {}개를 추가했습니다.", board.getName(), toAdd);
		}

		if (args.containsOption("app.sample-data.exit")) {
			// 데이터만 넣고 바로 종료
			System.exit(SpringApplication.exit(context, () -> 0));
		}
	}

	private void seed(Board board, int count, Generator gen, List<Member> staff, Member admin) {
		// 오래된 글부터 넣어서 글 번호와 작성일 순서가 맞게 한다
		LocalDateTime now = LocalDateTime.now();
		List<LocalDateTime> dates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			dates.add(now.minusMinutes(30 + random.nextInt(180 * 24 * 60)));
		}
		dates.sort(Comparator.naturalOrder());

		for (int i = 0; i < count; i++) {
			Member author = board.isAdminOnlyWrite() ? admin : pick(staff);
			Sample sample = gen.make(i, author);
			boolean pinned = board.isAdminOnlyWrite() && i >= count - 2; // 최근 공지 2개는 상단 고정
			Post post = postRepository.save(new Post(board, author, sample.title(), sample.content(), pinned));
			LocalDateTime createdAt = dates.get(i);
			int views = 3 + random.nextInt(pinned ? 300 : 120);
			jdbc.update("update post set created_at = ?, updated_at = ?, view_count = ? where id = ?",
				createdAt, createdAt, views, post.getId());

			int comments = sample.comments().isEmpty() ? 0 : random.nextInt(Math.min(6, sample.comments().size() + 1));
			LocalDateTime at = createdAt;
			for (int c = 0; c < comments; c++) {
				at = at.plusMinutes(5 + random.nextInt(600));
				if (at.isAfter(now)) {
					break;
				}
				Member commenter = pick(staff);
				Comment comment = commentRepository.save(new Comment(post, commenter, sample.comments().get(c)));
				jdbc.update("update post_comment set created_at = ? where id = ?", at, comment.getId());
			}
		}
	}

	// ---------- 내용 만들기 ----------

	private record Sample(String title, String content, List<String> comments) {
	}

	@FunctionalInterface
	private interface Generator {
		Sample make(int index, Member author);
	}

	private Generator generatorFor(String boardName) {
		if (boardName.contains("공지")) {
			return this::notice;
		}
		if (boardName.contains("경조사")) {
			return this::family;
		}
		if (boardName.contains("자료")) {
			return this::library;
		}
		if (boardName.contains("질문")) {
			return this::question;
		}
		return this::free;
	}

	private Sample notice(int i, Member author) {
		String[][] topics = {
			{"인사", "하반기 정기 인사 발령 안내", "아래와 같이 정기 인사 발령을 안내드립니다.\n\n- 발령일: 다음 달 1일\n- 대상: 첨부 명단 참고\n\n부서 이동이 있는 분들은 인수인계를 마무리해 주시기 바랍니다."},
			{"총무", "사무실 정기 소독 실시 안내", "쾌적한 근무 환경을 위해 사무실 정기 소독을 실시합니다.\n\n- 일시: 이번 주 토요일 10:00 ~ 14:00\n- 범위: 전 층 사무실, 회의실, 탕비실\n\n개인 물품은 서랍에 보관해 주세요."},
			{"보안", "개인정보 보호 교육 이수 안내", "법정 의무 교육인 개인정보 보호 교육을 이달 말까지 이수해 주시기 바랍니다.\n\n- 방법: 사내 교육 사이트 온라인 수강 (약 1시간)\n- 기한: 이번 달 말일\n\n미이수 시 인사 평가에 반영될 수 있습니다."},
			{"복지", "임직원 건강검진 일정 안내", "올해 임직원 건강검진 일정을 안내드립니다.\n\n- 기간: 다음 달 한 달간\n- 장소: 협력 검진센터 (예약 필수)\n- 대상: 전 임직원 (배우자 검진은 별도 신청)\n\n검진 당일은 공가로 처리됩니다."},
			{"IT", "사내 메일 시스템 정기 점검 안내", "안정적인 서비스 제공을 위해 메일 시스템 정기 점검을 진행합니다.\n\n- 일시: 이번 주 일요일 02:00 ~ 06:00\n- 영향: 점검 시간 동안 메일 송수신 불가\n\n업무에 참고 부탁드립니다."},
			{"경영", "분기 경영 실적 공유회 개최", "분기 경영 실적 공유회를 아래와 같이 개최합니다.\n\n- 일시: 다음 주 금요일 16:00\n- 장소: 대회의실 (온라인 동시 송출)\n\n전 임직원 참석 바랍니다."},
			{"총무", "주차장 이용 규정 변경 안내", "주차 공간 부족으로 주차장 이용 규정이 일부 변경됩니다.\n\n- 시행일: 다음 달 1일\n- 변경 내용: 요일제 운영 (차량 번호 끝자리 기준)\n\n자세한 내용은 총무팀에 문의해 주세요."},
			{"인사", "연차 사용 촉진 안내", "연말까지 미사용 연차가 남아 있는 분들은 사용 계획을 제출해 주시기 바랍니다.\n\n- 제출 기한: 이번 달 20일\n- 제출 방법: 전자결재 품의서\n\n팀장님들께서는 팀원 연차 사용을 독려해 주세요."},
			{"보안", "출입카드 재발급 절차 안내", "출입카드 분실 시 재발급 절차를 안내드립니다.\n\n1. 총무팀에 분실 신고\n2. 기존 카드 사용 정지\n3. 재발급 신청서 제출 (발급비 본인 부담)\n\n분실 즉시 신고 부탁드립니다."},
			{"복지", "사내 동호회 지원금 신청 안내", "하반기 사내 동호회 활동 지원금 신청을 받습니다.\n\n- 지원 대상: 회원 5인 이상 등록 동호회\n- 지원 금액: 1인당 월 2만 원\n- 신청 기한: 이번 달 말일\n\n활동 계획서를 함께 제출해 주세요."},
		};
		String[] t = topics[i % topics.length];
		int round = i / topics.length + 1;
		String title = "[" + t[0] + "] " + t[1] + (round > 1 ? " (" + round + "차)" : "");
		String content = "안녕하세요, 경영지원본부입니다.\n\n" + t[2] + "\n\n문의: 경영지원본부 (내선 1200)\n감사합니다.";
		return new Sample(title, content, List.of());
	}

	private Sample free(int i, Member author) {
		String[] places = {"회사 앞 국밥집", "2층 카페", "역 앞 칼국수집", "새로 생긴 샐러드 가게", "골목 안 돈가스집", "회사 뒤 분식집"};
		String[] hobbies = {"풋살", "등산", "독서", "보드게임", "러닝", "사진"};
		String[] trips = {"제주도", "강릉", "부산", "경주", "속초", "여수"};
		List<Function<Integer, Sample>> kinds = List.of(
			n -> new Sample("점심 맛집 추천합니다 - " + places[n % places.length],
				places[n % places.length] + " 다녀왔는데 가격도 괜찮고 양도 많아요.\n점심시간에는 조금 일찍 가야 자리가 있습니다.\n다들 한 번 가보세요!",
				List.of("저도 가봤는데 맛있었어요!", "오늘 점심 여기로 갑니다 ㅎㅎ", "웨이팅 얼마나 되나요?", "추천 감사합니다~", "메뉴 뭐가 제일 맛있나요?")),
			n -> new Sample("사내 " + hobbies[n % hobbies.length] + " 동호회 회원 모집합니다",
				hobbies[n % hobbies.length] + " 좋아하시는 분들 함께해요.\n한 달에 두 번 정도 모이고, 초보도 환영합니다.\n관심 있으신 분은 댓글 남겨 주세요.",
				List.of("저 참여하고 싶어요!", "초보도 괜찮을까요?", "모임 요일이 언제인가요?", "신청합니다 🙋", "지난번 모임 재밌었어요")),
			n -> new Sample(trips[n % trips.length] + " 여행 다녀온 후기",
				"연차 쓰고 " + trips[n % trips.length] + " 다녀왔습니다.\n날씨가 좋아서 바다 보기 딱 좋았어요.\n숙소랑 맛집 정보 필요하신 분은 말씀 주세요.",
				List.of("부럽습니다~", "사진도 올려주세요!", "저도 다음 달에 가려고요", "숙소 어디셨어요?")),
			n -> new Sample("탕비실 커피 머신 원두 바뀐 거 아시나요?",
				"이번 주부터 원두가 바뀌었네요.\n산미가 조금 있는 편인데 다들 어떠신가요?",
				List.of("저는 전보다 좋아요", "예전 원두가 그리워요 ㅠ", "라떼로 마시면 괜찮아요")),
			n -> new Sample("퇴근 후 운동 같이 하실 분",
				"회사 근처 헬스장 등록했는데 같이 다니실 분 계실까요?\n주 3회 정도 생각하고 있습니다.",
				List.of("저요!", "몇 시쯤 가시나요?", "작심삼일 방지 모임 좋네요"))
		);
		return kinds.get(i % kinds.size()).apply(i / kinds.size());
	}

	private Sample family(int i, Member author) {
		Member who = staff.get((i * 7) % staff.size());
		String person = who.getDepartment().getName() + " " + who.getName()
			+ (who.getGrade() == null ? "" : " " + who.getGrade().getName());
		String[][] kinds = {
			{"결혼", person + " 결혼합니다", "축하해 주세요!\n\n" + person + "님이 결혼합니다.\n\n- 일시: 다음 달 둘째 주 토요일 12:30\n- 장소: 시내 웨딩홀 3층\n\n많은 축하 부탁드립니다."},
			{"출산", person + " 득녀/득남 소식", person + "님 가정에 새 식구가 생겼습니다.\n산모와 아기 모두 건강하다고 합니다.\n따뜻한 축하 부탁드립니다."},
			{"부고", person + " 부친상", person + "님의 부친께서 별세하셨기에 삼가 알려드립니다.\n\n- 빈소: 시립병원 장례식장 2호실\n- 발인: 모레 오전 8시\n\n고인의 명복을 빕니다."},
			{"돌잔치", person + " 자녀 돌잔치 안내", person + "님 자녀의 첫 돌잔치가 열립니다.\n\n- 일시: 이번 주 토요일 18:00\n- 장소: 회사 근처 뷔페 2층\n\n시간 되시는 분들은 축하해 주세요."},
		};
		String[] k = kinds[i % kinds.length];
		List<String> comments = k[0].equals("부고")
			? List.of("삼가 고인의 명복을 빕니다.", "깊은 애도를 표합니다.", "힘내세요.", "삼가 조의를 표합니다.")
			: List.of("축하드립니다!", "진심으로 축하해요 🎉", "행복하세요~", "축하축하!", "정말 축하드립니다.");
		return new Sample("[" + k[0] + "] " + k[1], k[2], comments);
	}

	private Sample library(int i, Member author) {
		String[][] docs = {
			{"양식", "출장 신청서", "출장 신청 시 사용하는 양식입니다.\n출장 3일 전까지 전자결재로 제출해 주세요."},
			{"양식", "법인카드 사용 내역서", "월말 법인카드 정산용 양식입니다.\n영수증을 함께 첨부해 주세요."},
			{"매뉴얼", "그룹웨어 사용 가이드", "그룹웨어 주요 기능(게시판, 전자결재, 일정) 사용법을 정리했습니다.\n신규 입사자분들께 공유 부탁드립니다."},
			{"매뉴얼", "VPN 접속 방법", "재택 근무 시 사내망 접속 방법입니다.\n문제가 있으면 IT 담당자에게 문의하세요."},
			{"자료", "분기 영업 실적 요약", "분기 영업 실적을 요약한 자료입니다.\n대외비이므로 외부 공유를 금합니다."},
			{"자료", "신제품 소개 자료", "고객 미팅 시 활용할 수 있는 신제품 소개 자료입니다.\n최신 버전으로 갱신했습니다."},
			{"양식", "휴가 신청서", "연차/반차/특별휴가 신청 양식입니다.\n팀장 승인 후 인사팀에 제출해 주세요."},
			{"매뉴얼", "회의실 예약 방법", "회의실 예약 및 취소 방법을 안내합니다.\n예약 후 사용하지 않으면 반드시 취소해 주세요."},
		};
		String[] d = docs[i % docs.length];
		int ver = i / docs.length + 1;
		String title = "[" + d[0] + "] " + d[1] + (ver > 1 ? " v" + ver + ".0" : "");
		return new Sample(title, d[2] + "\n\n작성: " + author.getName(),
			List.of("감사합니다!", "잘 쓰겠습니다.", "최신 버전 맞나요?", "유용하네요."));
	}

	private Sample question(int i, Member author) {
		String[][] qa = {
			{"법인카드 사용 내역은 어디서 확인하나요?", "카드사 홈페이지에서 확인 가능합니다. 재무회계팀에 요청하시면 월별 내역도 받을 수 있어요."},
			{"재택 근무 신청은 어떻게 하나요?", "전자결재에서 품의서로 올리시면 됩니다. 팀장님 승인 후 인사팀 확인까지 받으세요."},
			{"모니터 추가 요청은 어디로 하나요?", "총무팀에 비품 요청서를 제출하시면 됩니다. 보통 1주일 정도 걸려요."},
			{"연말정산 서류 제출 기한이 언제인가요?", "보통 1월 말까지입니다. 인사팀 공지를 확인해 주세요."},
			{"회의실 장비(빔프로젝터) 사용법 아시는 분?", "리모컨 전원 버튼 누르고 HDMI 1번으로 바꾸시면 됩니다. 케이블은 서랍에 있어요."},
			{"출장비 정산은 언제까지 해야 하나요?", "출장 복귀 후 1주일 이내에 정산하셔야 합니다."},
			{"사내 Wi-Fi 비밀번호가 바뀌었나요?", "이번 달부터 바뀌었습니다. IT 공지 메일 확인해 보세요."},
		};
		String[] q = qa[i % qa.length];
		return new Sample("[질문] " + q[0],
			q[0] + "\n처음이라 잘 모르겠네요. 아시는 분 답변 부탁드립니다.",
			List.of(q[1], "저도 궁금했어요.", "답변 감사합니다!", "해결됐습니다 :)"));
	}

	private Member pick(List<Member> members) {
		return members.get(random.nextInt(members.size()));
	}
}
