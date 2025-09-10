package ao.co.isptec.aplm.sca;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import ao.co.isptec.aplm.sca.model.Ocorrencia;

public class ListaOcorrenciasAdapter extends RecyclerView.Adapter<ListaOcorrenciasAdapter.OcorrenciaViewHolder> {

    private List<Ocorrencia> listaOcorrencias;
    private OnOcorrenciaClickListener listener;
    private SimpleDateFormat dateFormat;

    public interface OnOcorrenciaClickListener {
        void onOcorrenciaClick(Ocorrencia ocorrencia);
    }

    public ListaOcorrenciasAdapter(List<Ocorrencia> listaOcorrencias, OnOcorrenciaClickListener listener) {
        this.listaOcorrencias = listaOcorrencias;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public OcorrenciaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ocorrencia, parent, false);
        return new OcorrenciaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OcorrenciaViewHolder holder, int position) {
        Ocorrencia ocorrencia = listaOcorrencias.get(position);
        
        holder.textDescricao.setText(ocorrencia.getDescricao());
        holder.textDataHora.setText(dateFormat.format(ocorrencia.getDataHora()));
        holder.textUrgencia.setText(ocorrencia.getUrgenciaTexto());
        holder.textContadorPartilha.setText("Partilhado " + ocorrencia.getContadorPartilha() + " vez(es)");

        // Set urgency color and icon
        if (ocorrencia.isUrgente()) {
            holder.textUrgencia.setTextColor(holder.itemView.getContext().getColor(R.color.urgent_color));
            holder.iconUrgencia.setImageResource(R.drawable.ic_priority_high);
        } else {
            holder.textUrgencia.setTextColor(holder.itemView.getContext().getColor(R.color.normal_color));
            holder.iconUrgencia.setImageResource(R.drawable.ic_priority_low);
        }

        // Set click listener
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOcorrenciaClick(ocorrencia);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaOcorrencias.size();
    }

    public static class OcorrenciaViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView textDescricao;
        TextView textDataHora;
        TextView textUrgencia;
        TextView textContadorPartilha;
        ImageView iconUrgencia;

        public OcorrenciaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardViewOcorrencia);
            textDescricao = itemView.findViewById(R.id.textDescricao);
            textDataHora = itemView.findViewById(R.id.textDataHora);
            textUrgencia = itemView.findViewById(R.id.textUrgencia);
            textContadorPartilha = itemView.findViewById(R.id.textContadorPartilha);

            iconUrgencia = itemView.findViewById(R.id.iconUrgencia);
        }
    }
}
