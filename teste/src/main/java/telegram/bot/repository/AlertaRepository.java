package telegram.bot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import telegram.bot.domain.Alerta;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findByHashConteudo(String hash);

    List<Alerta> findTop20ByOrderByDataHoraDesc();

    /** Versão paginada para respeitar um N arbitrário (ex.: PageRequest.of(0, n)). */
    List<Alerta> findByOrderByDataHoraDesc(Pageable p);

    List<Alerta> findTop20ByFonteOrderByDataHoraDesc(String fonte);

    List<Alerta> findByEnviadoFalse();
}
