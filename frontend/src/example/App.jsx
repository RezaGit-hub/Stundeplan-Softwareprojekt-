import { Routes, Route } from 'react-router-dom'
import Home from './pages/Home'
import ClientList from './pages/ClientList'
import ClientEdit from './pages/ClientEdit'
import Navbar from './components/Navbar'

export default function App() {
    return (
        <div className="min-h-screen bg-gray-50">
            <Navbar />
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/clients" element={<ClientList />} />
                <Route path="/clients/new" element={<ClientEdit />} />
                <Route path="/clients/:id" element={<ClientEdit />} />
            </Routes>
        </div>
    )
}