package com.roles.usermanagement.modules.ingreso;

/** Estados del ingreso (lista fija; Hibernate crea un CHECK en la base). RECUPERADO cierra el ingreso. */
public enum EstadoIngreso { INGRESADO, EN_TRATAMIENTO, EN_RECUPERACION, RECUPERADO }
