package th_prj.th_company.file;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

	List<FileAttachment> findByOwnerTypeAndOwnerIdOrderById(FileOwner ownerType, Long ownerId);
}
