package cl.duoc.catalogo.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Especie a la venta: copia de la ficha de API Ninjas más precio y stock.
 * Nunca se expone directamente en la API; siempre se mapea a un DTO.
 */
@Entity
@Table(name = "especie")
public class Especie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre exacto que devuelve API Ninjas; sirve para cruzar búsquedas con el catálogo. */
    @Column(name = "nombre_api", nullable = false, unique = true)
    private String nombreApi;

    @Column(name = "nombre_es", nullable = false)
    private String nombreEs;

    @Column(name = "nombre_cientifico", length = 500)
    private String nombreCientifico;

    @Column(length = 1000)
    private String habitat;

    @Column(length = 500)
    private String dieta;

    @Column(name = "velocidad_maxima", length = 500)
    private String velocidadMaxima;

    @Column(name = "esperanza_vida", length = 500)
    private String esperanzaVida;

    @ElementCollection
    @CollectionTable(name = "especie_ubicacion", joinColumns = @JoinColumn(name = "especie_id"))
    @Column(name = "ubicacion")
    private List<String> ubicaciones = new ArrayList<>();

    @Column(nullable = false)
    private Integer precio;

    @Column(nullable = false)
    private Integer stock;

    protected Especie() {
        // requerido por JPA
    }

    public Especie(String nombreApi, String nombreEs, String nombreCientifico, String habitat, String dieta,
                   String velocidadMaxima, String esperanzaVida, List<String> ubicaciones,
                   Integer precio, Integer stock) {
        this.nombreApi = nombreApi;
        this.nombreEs = nombreEs;
        this.nombreCientifico = nombreCientifico;
        this.habitat = habitat;
        this.dieta = dieta;
        this.velocidadMaxima = velocidadMaxima;
        this.esperanzaVida = esperanzaVida;
        this.ubicaciones = ubicaciones == null ? new ArrayList<>() : new ArrayList<>(ubicaciones);
        this.precio = precio;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public String getNombreApi() { return nombreApi; }
    public String getNombreEs() { return nombreEs; }
    public String getNombreCientifico() { return nombreCientifico; }
    public String getHabitat() { return habitat; }
    public String getDieta() { return dieta; }
    public String getVelocidadMaxima() { return velocidadMaxima; }
    public String getEsperanzaVida() { return esperanzaVida; }
    public List<String> getUbicaciones() { return ubicaciones; }
    public Integer getPrecio() { return precio; }
    public Integer getStock() { return stock; }
}
