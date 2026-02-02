import firebase_admin
from firebase_admin import credentials, db
import pandas as pd
import folium
from folium.plugins import HeatMap
import webbrowser
import os

# 1. Conexión a Firebase
cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://halcones-al-rescate-default-rtdb.firebaseio.com/' # Cópiala de la consola de Firebase
})

print("Descargando datos de la nube...")

# 2. Obtener datos
ref = db.reference('alertas')
data = ref.get()

if data:
    # 3. Procesar datos con Pandas
    incident_list = []
    for key, value in data.items():
        incident_list.append(value)
    
    df = pd.DataFrame(incident_list)
    print(f"Analizando {len(df)} alertas...")
    
    # 4. Crear Mapa base (Centrado en el promedio de las coordenadas)
    map_center = [df['latitud'].mean(), df['longitud'].mean()]
    my_map = folium.Map(location=map_center, zoom_start=15)

    # 5. Agregar capa de Heatmap
    heat_data = [[row['latitud'], row['longitud']] for index, row in df.iterrows()]
    HeatMap(heat_data, radius=15).add_to(my_map)

    # 6. Guardar y abrir
    output_file = "mapa_riesgo_campus.html"
    my_map.save(output_file)
    print(f"Mapa generado: {output_file}")
    
    # Abrir automáticamente en el navegador
    webbrowser.open('file://' + os.path.realpath(output_file))

else:
    print("No hay datos en Firebase todavía. Usa la app para generar alertas.")