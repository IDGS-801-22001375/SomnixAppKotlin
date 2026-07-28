package com.example.somnixapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.R
import com.example.somnixapp.databinding.ItemRutaBinding
import com.example.somnixapp.models.response.RutaResponse

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

        val origenNombre = ruta.origen.nombre
            .ifEmpty { ruta.origen.direccion }

        val destinoNombre = ruta.destino.nombre
            .ifEmpty { ruta.destino.direccion }

        holder.binding.txtNombreRuta.text = ruta.nombre

        holder.binding.txtOrigenDestino.text =
            "$origenNombre → $destinoNombre"

        holder.binding.txtDetalleRuta.text =
            "${ruta.distanciaKm} km • ${ruta.duracionMinutos} min"

        val rutaTerminada = ruta.estado.equals(
            "TERMINADA",
            ignoreCase = true
        )

        if (rutaTerminada) {
            configurarRutaTerminada(holder)
        } else {
            configurarRutaPendiente(holder)
        }

        holder.binding.btnGuardarRuta.setOnClickListener {
            onGuardarRutaClick(ruta)
        }

        holder.binding.btnVerMapa.setOnClickListener {
            onVerMapaClick(ruta)
        }
    }

    private fun configurarRutaPendiente(
        holder: RutaViewHolder
    ) {
        holder.binding.txtEstadoRuta.text = "Pendiente"

        holder.binding.txtEstadoRuta.setTextColor(
            Color.parseColor("#7A4D00")
        )

        holder.binding.txtEstadoRuta.setBackgroundResource(
            R.drawable.bg_badge_pendiente
        )

        holder.binding.txtDescripcionEstado.text =
            "Esta ruta se encuentra pendiente de realizar."

        /*
         * Cuando se abre desde Monitoreo,
         * mostramos Guardar ruta y Ver ruta en el mapa.
         */
        if (modoSeleccionRuta) {
            holder.binding.contenedorBotonesRuta.visibility = View.VISIBLE
            holder.binding.btnGuardarRuta.visibility = View.VISIBLE
            holder.binding.btnVerMapa.visibility = View.VISIBLE
        } else {
            /*
             * En teoría una ruta pendiente no debería llegar
             * a la pantalla de rutas realizadas.
             */
            holder.binding.contenedorBotonesRuta.visibility = View.GONE
        }
    }

    private fun configurarRutaTerminada(
        holder: RutaViewHolder
    ) {
        holder.binding.txtEstadoRuta.text = "Terminada"

        holder.binding.txtEstadoRuta.setTextColor(
            Color.parseColor("#166534")
        )

        holder.binding.txtEstadoRuta.setBackgroundResource(
            R.drawable.bg_badge_terminada
        )

        holder.binding.txtDescripcionEstado.text =
            "Esta ruta ya fue realizada y forma parte de tu historial."

        /*
         * En Mis rutas no mostramos Guardar ruta.
         * Dejamos únicamente la opción de ver el recorrido.
         */
        holder.binding.contenedorBotonesRuta.visibility = View.VISIBLE
        holder.binding.btnGuardarRuta.visibility = View.GONE
        holder.binding.btnVerMapa.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = rutas.size

    fun actualizarLista(nuevaLista: List<RutaResponse>) {
        rutas = nuevaLista
        notifyDataSetChanged()
    }
}