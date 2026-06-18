package telegram.bot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import telegram.bot.domain.ChatConfig;

@Repository
public interface ChatConfigRepository extends JpaRepository<ChatConfig, Long> {

    List<ChatConfig> findByAtivoTrue();
}
