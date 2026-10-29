# ADR-001: Arquitetura de microsserviços para o SertãoSense

- **Status:** Proposta
- **Data:** 2026-10-28
- **Autora:** Ketlin Oliveira

## Contexto

O SertãoSense é uma solução para monitorar cisternas no sertão da Bahia e alertar a população
quando o nível da água muda.

**O problema:** falta comunicação entre quem fornece a água das cisternas e a população que usa.
Hoje as pessoas são pegas de surpresa quando o nível de uma cisterna cai.

**Os sensores:** medem o nível da água. No começo eles serão simulados. Depois o projeto pode passar
a usar um sensor real com ESP.

**Volume esperado:**

| Cenário    | Cisternas monitoradas |
|------------|-----------------------|
| Simulação  | 40 a 50               |
| Teste real | 1                     |

**Motivação do projeto:** o principal motivo é aprender. O objetivo é construir uma tecnologia de
monitoramento usando arquitetura de microsserviços, banco por serviço, nuvem, Kafka para as leituras
dos sensores, RabbitMQ para as filas de alertas, além de Redis, Flyway e JWT.

## Decisão

O sistema será dividido em **4 microsserviços**, cada um com o **próprio banco de dados**.

### Recorte dos serviços

A divisão em 4 serviços vem de uma partição lógica do que cada serviço entrega. Assim, cada serviço
fica completo em si e não depende dos demais. Cada um tem regras de negócio próprias e definidas.
Juntar serviços tiraria essa autonomia e deixaria um dependente do outro.

| Serviço        | Responsabilidade |
|----------------|------------------|
| `dispositivos` | Cadastro das cisternas e dos sensores. |
| `ingestao`     | Recebe as leituras dos sensores e publica no Kafka. |
| `analise`      | Consome as leituras, avalia o nível e o consumo de água e dispara alertas via RabbitMQ. |
| `notificacoes` | Consome os alertas e notifica a população por e-mail e Telegram. |

Fluxo de uma leitura:

```
sensor ──REST──▶ API Gateway ──▶ ingestao ──Kafka──▶ analise ──RabbitMQ──▶ notificacoes → e-mail / Telegram
                     │
                     └──▶ dispositivos (cadastro de cisternas e sensores)
```

### Banco por serviço

Cada serviço terá o próprio banco. Isso impede que outros serviços acessem o banco diretamente:
todo acesso passa pela regra de negócio exposta na API do serviço. Isso mantém o encapsulamento e
evita acoplamento de schemas. Se uma tabela mudar, os outros serviços não são afetados.

Escolhemos esse modelo **aceitando os custos**:

- Não é possível fazer `JOIN` entre dados de serviços diferentes.
- Pode haver duplicação de dados entre serviços.

### Comunicação entre serviços

| Fluxo                      | Tipo      | Tecnologia |
|----------------------------|-----------|------------|
| `ingestao` → `analise`     | Assíncrono | Kafka (leituras dos sensores) |
| `analise` → `notificacoes` | Assíncrono | RabbitMQ (fila de alertas) |
| Clientes externos (sensores, dashboard) → serviços | Síncrono | REST, via API Gateway |

Não há chamadas REST entre os serviços. O REST existe só na entrada do sistema, passando pelo
API Gateway.

Por enquanto, `dispositivos` não se comunica com `analise`.

## Alternativas consideradas

### Monólito modular (descartado)

O monólito modular foi considerado, mas descartado. Para o volume atual (até 50 cisternas), um
monólito modular atenderia. **A escolha por microsserviços foi motivada pelo aprendizado.**

Mesmo assim, a arquitetura traz ganhos de autonomia:

- Se o serviço de análise atrasar, o serviço de ingestão continua funcionando sozinho.
- Se for preciso expandir o serviço de ingestão, a mudança afeta só ele.

Assim o projeto continua escalável, com serviços distribuídos.

## Consequências

### Positivas

- Mais controle sobre o nível das cisternas.
- Mais transparência para os cidadãos que usam as cisternas.

### Negativas

- **Operações distribuídas complexas:** é mais difícil rastrear e depurar fluxos que passam por
  vários serviços.
- **Latência:** chamadas remotas pela rede adicionam sobrecarga de desempenho, e a latência se
  acumula a cada salto entre serviços.
- **Complexidade operacional e de observabilidade:** 4 serviços exigem investimento em
  infraestrutura, pipeline, deploy etc.

### Quando revisitar esta decisão

Esta decisão deve ser revista se:

- O custo de nuvem para manter 4 serviços ficar alto demais para o projeto.
- A sobrecarga operacional ficar maior do que o benefício que a arquitetura traz.
- A `analise` passar a precisar de dados do cadastro (ex.: capacidade da cisterna). Nesse caso,
  `dispositivos` deve publicar eventos no Kafka e a `analise` deve manter uma cópia local, para não
  depender de `dispositivos` estar no ar.
