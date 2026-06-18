package telegram.bot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import telegram.bot.domain.FiltroAssunto;

@Repository
public interface FiltroAssuntoRepository extends JpaRepository<FiltroAssunto, Long> {
    List<FiltroAssunto> findByAtivoTrue();
}
