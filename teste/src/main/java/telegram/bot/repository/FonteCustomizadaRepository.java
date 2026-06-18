package telegram.bot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import telegram.bot.domain.FonteCustomizada;

@Repository
public interface FonteCustomizadaRepository extends JpaRepository<FonteCustomizada, Long> {
    List<FonteCustomizada> findByAtivoTrue();
}
