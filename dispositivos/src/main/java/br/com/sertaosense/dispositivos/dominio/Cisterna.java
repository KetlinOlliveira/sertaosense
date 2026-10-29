package br.com.sertaosense.dispositivos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

// Tabela criada pela V1 e ajustada pela V3. Sem @Data de propósito: equals/hashCode/toString
// gerados em entidades JPA causam problemas (proxies, relacionamentos lazy). Sem setters públicos:
// mudanças de estado devem entrar por métodos com nome de negócio.
@Entity
@Table(name = "cisterna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // o JPA exige; protected impede uso no resto do código
public class Cisterna {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // gerado pelo Hibernate antes do INSERT
    private UUID id;

    private String nome;

    private String comunidade;

    // capacidadeLitros -> capacidade_litros: o Spring converte camelCase em snake_case sozinho
    private Integer capacidadeLitros;

    // BigDecimal porque a coluna é NUMERIC(9,6); Double viraria float8 e o ddl-auto=validate falharia
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    // O DEFAULT TRUE do banco não vale aqui: o Hibernate envia o valor do campo no INSERT,
    // então o padrão precisa existir também no Java.
    private boolean ativa = true;

    public Cisterna(String nome, String comunidade, Integer capacidadeLitros,
                    BigDecimal latitude, BigDecimal longitude) {
        this.nome = nome;
        this.comunidade = comunidade;
        this.capacidadeLitros = capacidadeLitros;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
