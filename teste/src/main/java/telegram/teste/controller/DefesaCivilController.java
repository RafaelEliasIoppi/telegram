package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import telegram.teste.service.DefesaCivilService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/defesa-civil")
public class DefesaCivilController {

    @Autowired
    private DefesaCivilService defesaCivilService;

    /**
     * Endpoint para buscar os avisos atuais da Defesa Civil RS.
     * Exemplo: GET /defesa-civil/avisos
     */
    @GetMapping("/avisos")
    public List<Map<String, String>> getAvisos() {
        return defesaCivilService.buscarAvisos();
    }

    /**
     * Endpoint para verificar se há novos avisos e enviar alerta no Telegram.
     * Exemplo: GET /defesa-civil/check
     */
    @GetMapping("/check")
    public String checkNovosAvisos() {
        return defesaCivilService.verificarNovosAvisos();
    }
}
