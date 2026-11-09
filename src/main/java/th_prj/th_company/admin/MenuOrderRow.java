package th_prj.th_company.admin;

/**
 * 메뉴 순서 화면의 한 줄. 사용자 메뉴와 관리자 메뉴가 같은 화면을 쓴다.
 *
 * @param name   저장할 때 보내는 값 (enum 이름)
 * @param detail 메뉴 이름 옆의 설명 (주소 등)
 */
public record MenuOrderRow(String name, String label, String detail) {
}
