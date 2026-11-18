package com.icarucci.caosennochebuena;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita las tareas que Spring ejecutará automáticamente,
// aunque ningún jugador esté enviando mensajes.
@Configuration
@EnableScheduling
public class ConfiguracionDeTareas {
}