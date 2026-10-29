package br.com.sertaosense.dispositivos.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Tabela criada pela V2. Relacionamento unidirecional: o Sensor conhece sua Cisterna,
// a Cisterna não tem lista de sensores (evita carregar coleções sem necessidade).
@Entity
@Table(name = "sensor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String codigo;

    // STRING grava "BOIA"; o padrão (ORDINAL) gravaria 0, 1, 2... e quebraria se a ordem do enum mudasse
    @Enumerated(EnumType.STRING)
    private TipoSensor tipo;

    // @ManyToOne é EAGER por padrão (faria JOIN em toda consulta de sensor); LAZY só busca se for usado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cisterna_id")
    private Cisterna cisterna;

    public Sensor(String codigo, TipoSensor tipo, Cisterna cisterna) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.cisterna = cisterna;
    }
}
