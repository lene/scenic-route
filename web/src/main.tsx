import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import 'maplibre-gl/dist/maplibre-gl.css'

const el = document.getElementById('root')
if (el) {
  ReactDOM.createRoot(el).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
}
