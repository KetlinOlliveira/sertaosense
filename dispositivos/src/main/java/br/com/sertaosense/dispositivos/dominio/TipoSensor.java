package br.com.sertaosense.dispositivos.dominio;

// Gravado no banco pelo NOME (ver @Enumerated em Sensor). Os valores precisam bater com o
// CHECK ck_sensor_tipo da V2: um valor novo aqui exige uma migration nova.
public enum TipoSensor {
    BOIA,
    ULTRASSONICO,
    PRESSAO,
    CAPACITIVO
}
