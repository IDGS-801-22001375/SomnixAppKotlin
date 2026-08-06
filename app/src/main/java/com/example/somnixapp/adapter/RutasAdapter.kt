package com.example.somnixapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.R
import com.example.somnixapp.databinding.ItemRutaBinding
import com.example.somnixapp.models.response.RutaResponse
import com.example.somnixapp.models.rutas.PuntoRuta

class RutasAdapter(
    private var rutas: List<RutaResponse>,
    private val modoSeleccionRuta: Boolean,
    private val onGuardarRutaClick: (RutaResponse) -> Unit,
    private val onVerMapaClick: (RutaResponse) -> Unit
) : RecyclerView.Adapter<RutasAdapter.RutaViewHolder>() {

    inner class RutaViewHolder(
        val binding: ItemRutaBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RutaViewHolder {
        val binding = ItemRutaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return RutaViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RutaViewHolder,
        position: Int
    ) {
        val ruta = rutas[position]

        val origenNombre = obtenerNombrePunto(
            punto = ruta.origen,
            textoAlternativo = ruta.origenTexto,
            textoPredeterminado = "Origen no disponible"
        )

        val destinoNombre = obtenerNombrePunto(
            punto = ruta.destino,
            textoAlternativo = ruta.destinoTexto,
            textoPredeterminado = "Destino no disponible"
        )

        val nombreRuta = ruta.nombre
            .takeIf { it.isNotBlank() }
            ?: "$origenNombre - $destinoNombre"

        holder.binding.txtNombreRuta.text =
            nombreRuta

        holder.binding.txtOrigenDestino.text =
            "$origenNombre → $destinoNombre"

        holder.binding.txtDetalleRuta.text =
            "${formatearDistancia(ruta.distanciaKm)} km • " +
                    "${ruta.duracionMinutos} min"

        when {
            ruta.estado.equals(
                "TERMINADA",
                ignoreCase = true
            ) -> {
                configurarRutaTerminada(holder)
            }

            ruta.estado.equals(
                "ASIGNADA",
                ignoreCase = true
            ) -> {
                configurarRutaAsignada(holder)
            }

            else -> {
                configurarRutaPendiente(holder)
            }
        }

        holder.binding.btnGuardarRuta.setOnClickListener {
            onGuardarRutaClick(ruta)
        }

        holder.binding.btnVerMapa.setOnClickListener {
            onVerMapaClick(ruta)
        }
    }

    private fun obtenerNombrePunto(
        punto: PuntoRuta?,
        textoAlternativo: String,
        textoPredeterminado: String
    ): String {
        if (textoAlternativo.isNotBlank()) {
            return textoAlternativo
        }

        if (punto == null) {
            return textoPredeterminado
        }

        return punto.nombre
            .takeIf { it.isNotBlank() }
            ?: punto.direccion
                .takeIf { it.isNotBlank() }
            ?: textoPredeterminado
    }

    private fun configurarRutaAsignada(
        holder: RutaViewHolder
    ) {
        holder.binding.txtEstadoRuta.text =
            "Asignada"

        holder.binding.txtEstadoRuta.setTextColor(
            Color.parseColor("#7A4D00")
        )

        holder.binding.txtEstadoRuta.setBackgroundResource(
            R.drawable.bg_badge_pendiente
        )

        holder.binding.txtDescripcionEstado.text =
            "Esta ruta se encuentra asignada y pendiente de realizar."

        if (modoSeleccionRuta) {
            holder.binding.contenedorBotonesRuta.visibility =
                View.VISIBLE

            holder.binding.btnGuardarRuta.visibility =
                View.VISIBLE

            holder.binding.btnVerMapa.visibility =
                View.VISIBLE
        } else {
            holder.binding.contenedorBotonesRuta.visibility =
                View.GONE
        }
    }

    private fun configurarRutaPendiente(
        holder: RutaViewHolder
    ) {
        holder.binding.txtEstadoRuta.text =
            "Pendiente"

        holder.binding.txtEstadoRuta.setTextColor(
            Color.parseColor("#7A4D00")
        )

        holder.binding.txtEstadoRuta.setBackgroundResource(
            R.drawable.bg_badge_pendiente
        )

        holder.binding.txtDescripcionEstado.text =
            "Esta ruta se encuentra pendiente de realizar."

        if (modoSeleccionRuta) {
            holder.binding.contenedorBotonesRuta.visibility =
                View.VISIBLE

            holder.binding.btnGuardarRuta.visibility =
                View.VISIBLE

            holder.binding.btnVerMapa.visibility =
                View.VISIBLE
        } else {
            holder.binding.contenedorBotonesRuta.visibility =
                View.GONE
        }
    }

    private fun configurarRutaTerminada(
        holder: RutaViewHolder
    ) {
        holder.binding.txtEstadoRuta.text =
            "Terminada"

        holder.binding.txtEstadoRuta.setTextColor(
            Color.parseColor("#166534")
        )

        holder.binding.txtEstadoRuta.setBackgroundResource(
            R.drawable.bg_badge_terminada
        )

        holder.binding.txtDescripcionEstado.text =
            "Esta ruta ya fue realizada y forma parte de tu historial."

        holder.binding.contenedorBotonesRuta.visibility =
            View.VISIBLE

        holder.binding.btnGuardarRuta.visibility =
            View.GONE

        holder.binding.btnVerMapa.visibility =
            View.VISIBLE
    }

    private fun formatearDistancia(
        distancia: Double
    ): String {
        return if (distancia % 1.0 == 0.0) {
            distancia.toInt().toString()
        } else {
            String.format(
                java.util.Locale.US,
                "%.1f",
                distancia
            )
        }
    }

    override fun getItemCount(): Int {
        return rutas.size
    }

    fun actualizarLista(
        nuevaLista: List<RutaResponse>
    ) {
        rutas = nuevaLista
        notifyDataSetChanged()
    }
}