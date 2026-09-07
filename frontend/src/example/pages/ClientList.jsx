import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

export default function ClientList() {
	const [clients, setClients] = useState([])
	const [loading, setLoading] = useState(true)

	useEffect(() => {
		fetch('/api/clients')
		.then(res => res.json())
		.then(data => {
			setClients(data)
			setLoading(false)
		})
	}, []) // empty array = run once, when the component first mounts

	async function remove(id) {
		await fetch(`/api/clients/${id}`, { method: 'DELETE' })
		setClients(clients.filter(c => c.id !== id))
	}

	if (loading) return <p className="p-8">Loading...</p>

	return (
		<div className="p-8">
			<div className="flex justify-between items-center mb-4">
				<h2 className="text-xl font-bold">Clients</h2>
				<Link
				to="/clients/new"
				className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700"
				>
					Add Client
				</Link>
			</div>

			<table className="w-full border-collapse bg-white shadow rounded overflow-hidden">
				<thead>
				<tr className="bg-gray-100 text-left">
					<th className="p-3">Name</th>
					<th className="p-3">Email</th>
					<th className="p-3">Actions</th>
				</tr>
				</thead>
				<tbody>
					{clients.map(client => (
						<tr key={client.id} className="border-t">
						<td className="p-3">{client.name}</td>
						<td className="p-3">{client.email}</td>
						<td className="p-3 flex gap-3">
							<Link to={`/clients/${client.id}`} className="text-blue-600 hover:underline">
							Edit
							</Link>
							<button onClick={() => remove(client.id)} className="text-red-600 hover:underline">
							Delete
							</button>
						</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}