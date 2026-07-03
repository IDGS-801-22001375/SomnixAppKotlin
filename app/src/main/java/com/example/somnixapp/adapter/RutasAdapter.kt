package com.example.somnixapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.somnixapp.databinding.ItemRutaBinding
import com.example.somnixapp.models.response.RutaResponse

class RutasAdapter(
    private var rutas: List<RutaResponse>,
    private val onEditarClick: (RutaResponse) -> Unit,
    private val onEliminarClick: (RutaResponse) -> Unit
) : RecyclerView.Adapter<RutasAdapter.RutaViewHolder>() {

    inner class RutaViewHolder(val binding: ItemRutaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RutaViewHolder {
        val binding = ItemRutaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RutaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RutaViewHolder, position: Int) {
        val ruta = rutas[position]

        holder.binding.txtNombreRuta.text = ruta.nombre
        holder.binding.txtOrigenDestino.text = "${ruta.origen} → ${ruta.destino}"
        holder.binding.txtDetalleRuta.text = "${ruta.distanciaKm} km • ${ruta.duracionMinutos} min"
        holder.binding.txtEstadoRuta.text = ruta.estado

        holder.binding.btnEditarRuta.setOnClickListener {
            onEditarClick(ruta)
        }

        holder.binding.btnEliminarRuta.setOnClickListener {
            onEliminarClick(ruta)
        }
    }

    override fun getItemCount(): Int = rutas.size

    fun actualizarLista(nuevaLista: List<RutaResponse>) {
        rutas = nuevaLista
        notifyDataSetChanged()
    }
}