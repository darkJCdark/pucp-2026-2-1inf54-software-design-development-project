package com.pucp.paqrap.modulos.incidencias.service;

import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownTypeSettingsEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownTypeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConfiguracionAveriaService {
    private final BreakdownTypeSettingsRepository repository;
    public ConfiguracionAveriaService(BreakdownTypeSettingsRepository repository) { this.repository = repository; }
    public int duracionMenorMinutos() {
        return repository.findById("MINOR").map(BreakdownTypeSettingsEntity::getRepairMinutes)
                .orElse((int) BreakdownType.MINOR.minimumUnavailable().toMinutes());
    }
    @Transactional
    public int actualizarMenor(int minutos) {
        return repository.save(new BreakdownTypeSettingsEntity(minutos)).getRepairMinutes();
    }
}