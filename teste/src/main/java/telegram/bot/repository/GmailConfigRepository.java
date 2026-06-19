package telegram.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import telegram.bot.domain.GmailConfig;

@Repository
public interface GmailConfigRepository extends JpaRepository<GmailConfig, Long> {
}
